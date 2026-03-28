package com.defenderlink.mesh.gossip;

import com.defenderlink.mesh.identity.NodeIdentity;
import com.defenderlink.mesh.ledger.consensus.RaftConsensus;
import com.defenderlink.mesh.ledger.model.Block;
import com.defenderlink.mesh.ledger.model.LedgerEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP broadcast-based gossip protocol for LAN peer discovery.
 *
 * Message types:
 * - ANNOUNCE: "I exist, here's my identity and endpoints"
 * - HEARTBEAT: "I'm the leader for term N"
 * - VOTE_REQ: "I want to be leader for term N"
 * - VOTE_RESP: "I vote for you / I don't"
 * - BLOCK: "Here's a new block to append"
 * - ENTRY_FWD: "Forward this entry to the leader"
 *
 * Simple and correct for LAN. WAN would need SWIM or similar.
 */
@ApplicationScoped
public class GossipService {

    private static final Logger log = LoggerFactory.getLogger(GossipService.class);
    private static final int MAX_PACKET = 65000;

    @ConfigProperty(name = "mesh.gossip.port", defaultValue = "9450")
    int gossipPort;

    @ConfigProperty(name = "mesh.gossip.peer-timeout-ms", defaultValue = "30000")
    long peerTimeoutMs;

    @Inject
    NodeIdentity identity;

    @Inject
    RaftConsensus raft;

    private DatagramSocket socket;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    void onStart(@Observes StartupEvent ev) {
        try {
            socket = new DatagramSocket(gossipPort);
            socket.setBroadcast(true);
            socket.setSoTimeout(100);
            running = true;

            // Start receiver on virtual thread
            Thread.ofVirtual().name("gossip-receiver").start(this::receiveLoop);
            raft.init();

            log.info("Gossip service started on UDP port {}", gossipPort);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start gossip service", e);
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        running = false;
        if (socket != null) socket.close();
    }

    // =========================================================================
    // Outbound Messages
    // =========================================================================

    /** Broadcast node announcement (peer discovery) */
    @Scheduled(every = "${mesh.gossip.broadcast-interval-ms:5000}ms")
    public void announcePresence() {
        var msg = Map.of(
                "type", "ANNOUNCE",
                "nodeId", identity.getNodeId(),
                "pubkey", identity.getPublicKeyBase64(),
                "port", gossipPort,
                "timestamp", Instant.now().toEpochMilli()
        );
        broadcast(msg);
    }

    /** Broadcast Raft heartbeat (leader only) */
    public void broadcastHeartbeat(String leaderId, long term) {
        broadcast(Map.of("type", "HEARTBEAT", "leaderId", leaderId, "term", term));
    }

    /** Broadcast vote request (candidate) */
    public void requestVotes(String candidateId, long term, long lastBlockIndex) {
        broadcast(Map.of(
                "type", "VOTE_REQ", "candidateId", candidateId,
                "term", term, "lastBlockIndex", lastBlockIndex
        ));
    }

    /** Broadcast a new block (leader) */
    public void broadcastBlock(Block block) {
        try {
            var msg = Map.of("type", "BLOCK", "block", mapper.writeValueAsString(block));
            broadcast(msg);
        } catch (Exception e) {
            log.error("Failed to broadcast block", e);
        }
    }

    /** Forward entry to leader */
    public void forwardEntryToLeader(String leaderId, LedgerEntry entry) {
        PeerInfo leader = peers.get(leaderId);
        if (leader == null) {
            log.warn("Leader {} not found in peer list", leaderId.substring(0, 16));
            return;
        }
        try {
            var msg = Map.of("type", "ENTRY_FWD", "entry", mapper.writeValueAsString(entry));
            sendTo(msg, leader.address, leader.port);
        } catch (Exception e) {
            log.error("Failed to forward entry to leader", e);
        }
    }

    public Collection<PeerInfo> getKnownPeers() {
        return peers.values();
    }

    /** Prune dead peers */
    @Scheduled(every = "10s")
    void prunePeers() {
        long now = System.currentTimeMillis();
        peers.entrySet().removeIf(e -> (now - e.getValue().lastSeen) > peerTimeoutMs);
    }

    // =========================================================================
    // Receiver Loop (virtual thread)
    // =========================================================================

    private void receiveLoop() {
        byte[] buf = new byte[MAX_PACKET];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                handleMessage(data, packet.getAddress(), packet.getPort());
            } catch (SocketTimeoutException ignored) {
            } catch (Exception e) {
                if (running) log.debug("Gossip receive error: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String data, InetAddress sender, int senderPort) {
        try {
            Map<String, Object> msg = mapper.readValue(data, Map.class);
            String type = (String) msg.get("type");
            if (type == null) return;

            switch (type) {
                case "ANNOUNCE" -> handleAnnounce(msg, sender, senderPort);
                case "HEARTBEAT" -> handleHeartbeat(msg);
                case "VOTE_REQ" -> handleVoteRequest(msg, sender, senderPort);
                case "VOTE_RESP" -> handleVoteResponse(msg);
                case "BLOCK" -> handleBlock(msg);
                case "ENTRY_FWD" -> handleEntryForward(msg);
            }
        } catch (Exception e) {
            log.debug("Failed to handle gossip message: {}", e.getMessage());
        }
    }

    private void handleAnnounce(Map<String, Object> msg, InetAddress sender, int senderPort) {
        String nodeId = (String) msg.get("nodeId");
        if (nodeId == null || nodeId.equals(identity.getNodeId())) return;

        peers.compute(nodeId, (k, v) -> {
            if (v == null) {
                log.info("Discovered peer: {} at {}:{}", nodeId.substring(0, 16),
                        sender.getHostAddress(), senderPort);
            }
            return new PeerInfo(nodeId, sender, senderPort,
                    (String) msg.get("pubkey"), System.currentTimeMillis());
        });
    }

    private void handleHeartbeat(Map<String, Object> msg) {
        String leaderId = (String) msg.get("leaderId");
        long term = ((Number) msg.get("term")).longValue();
        raft.onHeartbeat(leaderId, term);
    }

    private void handleVoteRequest(Map<String, Object> msg, InetAddress sender, int senderPort) {
        String candidateId = (String) msg.get("candidateId");
        if (candidateId.equals(identity.getNodeId())) return; // ignore own vote requests
        long term = ((Number) msg.get("term")).longValue();
        long lastBlockIndex = ((Number) msg.get("lastBlockIndex")).longValue();

        boolean granted = raft.onVoteRequest(candidateId, term, lastBlockIndex);
        try {
            sendTo(Map.of("type", "VOTE_RESP", "granted", granted,
                    "term", term, "voterId", identity.getNodeId()), sender, senderPort);
        } catch (Exception e) {
            log.error("Failed to send vote response", e);
        }
    }

    private void handleVoteResponse(Map<String, Object> msg) {
        boolean granted = (boolean) msg.get("granted");
        raft.onVoteResponse(granted);
        if (granted) {
            long term = ((Number) msg.get("term")).longValue();
            // Simplified: in a full implementation, count votes and call becomeLeader
            // For LAN with small node count, single vote + self is often enough
            int totalPeers = peers.size();
            if (totalPeers <= 2) {
                raft.becomeLeader(term);
            }
        }
    }

    private void handleBlock(Map<String, Object> msg) {
        try {
            String blockJson = (String) msg.get("block");
            Block block = mapper.readValue(blockJson, Block.class);
            raft.onBlockProposed(block);
        } catch (Exception e) {
            log.error("Failed to process incoming block", e);
        }
    }

    private void handleEntryForward(Map<String, Object> msg) {
        try {
            String entryJson = (String) msg.get("entry");
            LedgerEntry entry = mapper.readValue(entryJson, LedgerEntry.class);
            raft.acceptForwardedEntry(entry);
        } catch (Exception e) {
            log.error("Failed to process forwarded entry", e);
        }
    }

    // =========================================================================
    // Transport
    // =========================================================================

    private void broadcast(Map<String, ?> msg) {
        try {
            byte[] data = mapper.writeValueAsBytes(msg);
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName("255.255.255.255"), gossipPort);
            socket.send(packet);
        } catch (Exception e) {
            log.debug("Broadcast failed: {}", e.getMessage());
        }
    }

    private void sendTo(Map<String, ?> msg, InetAddress addr, int port) throws Exception {
        byte[] data = mapper.writeValueAsBytes(msg);
        socket.send(new DatagramPacket(data, data.length, addr, port));
    }

    // =========================================================================
    // Peer Record
    // =========================================================================
    public record PeerInfo(String nodeId, InetAddress address, int port,
                           String pubkey, long lastSeen) {}
}