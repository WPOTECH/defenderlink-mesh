package com.defenderlink.mesh.ledger.consensus;

import com.defenderlink.mesh.gossip.GossipService;
import com.defenderlink.mesh.identity.NodeIdentity;
import com.defenderlink.mesh.ledger.model.Block;
import com.defenderlink.mesh.ledger.model.LedgerEntry;
import com.defenderlink.mesh.ledger.store.LedgerStore;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simplified Raft consensus for ledger block ordering.
 *
 * Only the leader can propose blocks. Any node can become leader via election.
 * This is NOT full Raft (no log replication with term/index matching) — it's
 * simplified for our use case where:
 * - Blocks are small and infrequent (network config changes, not transactions)
 * - All nodes hold full replicas
 * - Consistency is more important than throughput
 *
 * States: FOLLOWER → CANDIDATE → LEADER
 *
 * Election: Random timeout (1.5-3s). If no heartbeat from leader,
 * node becomes candidate and requests votes. Majority wins.
 *
 * Block proposal: Leader collects pending entries, creates block,
 * broadcasts to all followers. Followers validate and append.
 */
@ApplicationScoped
public class RaftConsensus {

    private static final Logger log = LoggerFactory.getLogger(RaftConsensus.class);

    public enum State { FOLLOWER, CANDIDATE, LEADER }

    @Inject
    NodeIdentity identity;

    @Inject
    LedgerStore ledger;

    @Inject
    GossipService gossip;

    @ConfigProperty(name = "mesh.consensus.election-timeout-min-ms", defaultValue = "2000")
    int electionTimeoutMin;

    @ConfigProperty(name = "mesh.consensus.election-timeout-max-ms", defaultValue = "4000")
    int electionTimeoutMax;

    // Raft state
    private final AtomicReference<State> state = new AtomicReference<>(State.FOLLOWER);
    private final AtomicLong currentTerm = new AtomicLong(0);
    private volatile String votedFor = null;
    private volatile String currentLeader = null;
    private volatile long lastHeartbeat = System.currentTimeMillis();

    // Pending entries waiting to be included in next block
    private final ConcurrentLinkedQueue<LedgerEntry> pendingEntries = new ConcurrentLinkedQueue<>();

    // Election config
    private final Random random = new Random();
    private volatile long electionTimeout;

    public void init() {
        resetElectionTimeout();
        log.info("Raft consensus initialized for node {}", identity.shortId());
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Submit an entry to be included in the next block */
    public CompletableFuture<Block> submitEntry(LedgerEntry entry) {
        if (state.get() == State.LEADER) {
            pendingEntries.add(entry);
            return CompletableFuture.completedFuture(null); // will be in next block
        } else if (currentLeader != null) {
            // Forward to leader via gossip
            gossip.forwardEntryToLeader(currentLeader, entry);
            return CompletableFuture.completedFuture(null);
        } else {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No leader available — election in progress"));
        }
    }

    /** Accept a forwarded entry from another node (leader only) */
    public void acceptForwardedEntry(LedgerEntry entry) {
        if (state.get() == State.LEADER) {
            pendingEntries.add(entry);
        }
    }

    /** Process incoming heartbeat from leader */
    public void onHeartbeat(String leaderId, long term) {
        // Ignore our own heartbeats
        if (leaderId.equals(identity.getNodeId())) return;

        if (term >= currentTerm.get()) {
            currentTerm.set(term);
            currentLeader = leaderId;
            state.set(State.FOLLOWER);
            votedFor = null;
            lastHeartbeat = System.currentTimeMillis();
        }
    }

    /** Process vote request from a candidate */
    public boolean onVoteRequest(String candidateId, long term, long lastBlockIndex) {
        if (term <= currentTerm.get()) return false;
        if (lastBlockIndex < ledger.getChainHeight()) return false;

        if (votedFor == null || votedFor.equals(candidateId)) {
            currentTerm.set(term);
            votedFor = candidateId;
            state.set(State.FOLLOWER);
            lastHeartbeat = System.currentTimeMillis();
            log.info("Voted for {} in term {}", candidateId.substring(0, 16), term);
            return true;
        }
        return false;
    }

    /** Process vote response */
    public void onVoteResponse(boolean granted) {
        // Simplified: vote counting happens in the election scheduler
    }

    /** Accept a block proposed by the leader */
    public boolean onBlockProposed(Block block) {
        boolean accepted = ledger.acceptBlock(block);
        if (accepted) {
            lastHeartbeat = System.currentTimeMillis();
        }
        return accepted;
    }

    public State getState() { return state.get(); }
    public String getCurrentLeader() { return currentLeader; }
    public long getCurrentTerm() { return currentTerm.get(); }
    public boolean isLeader() { return state.get() == State.LEADER; }

    // =========================================================================
    // Scheduled Tasks
    // =========================================================================

    /** Election timeout check — runs every 1s */
    @Scheduled(every = "1s")
    void electionCheck() {
        if (state.get() == State.LEADER) return;

        long elapsed = System.currentTimeMillis() - lastHeartbeat;
        if (elapsed > electionTimeout) {
            startElection();
        }
    }

    /** Leader heartbeat + block creation — runs every 1s */
    @Scheduled(every = "1s")
    void leaderDuties() {
        if (state.get() != State.LEADER) return;

        // Send heartbeat
        gossip.broadcastHeartbeat(identity.getNodeId(), currentTerm.get());

        // Collect pending entries and create block if any
        if (!pendingEntries.isEmpty()) {
            List<LedgerEntry> entries = new ArrayList<>();
            LedgerEntry e;
            while ((e = pendingEntries.poll()) != null && entries.size() < 100) {
                entries.add(e);
            }

            if (!entries.isEmpty()) {
                Block block = ledger.appendBlock(entries);
                gossip.broadcastBlock(block);
                log.info("Leader proposed block {} with {} entries",
                        block.index(), entries.size());
            }
        }
    }

    // =========================================================================
    // Election
    // =========================================================================

    private void startElection() {
        long newTerm = currentTerm.incrementAndGet();
        state.set(State.CANDIDATE);
        votedFor = identity.getNodeId();
        currentLeader = null;
        resetElectionTimeout();

        int peerCount = gossip.getKnownPeers().size();
        int votesNeeded = (peerCount / 2) + 1;

        log.info("Starting election for term {} (need {} votes from {} peers)",
                newTerm, votesNeeded, peerCount);

        // Request votes from all known peers
        int votesReceived = 1; // vote for self
        // In simplified model: gossip handles vote collection
        gossip.requestVotes(identity.getNodeId(), newTerm, ledger.getChainHeight());

        // For single-node or small network: become leader immediately
        if (peerCount == 0 || votesReceived >= votesNeeded) {
            becomeLeader(newTerm);
        }

        // Multi-node election: votes arrive async via gossip → onVoteResponse
        // The gossip layer tracks vote count and calls becomeLeader when majority reached
    }

    public void becomeLeader(long term) {
        if (currentTerm.get() != term) return;
        state.set(State.LEADER);
        currentLeader = identity.getNodeId();
        lastHeartbeat = System.currentTimeMillis();
        log.info("*** Node {} elected leader for term {} ***",
                identity.shortId(), term);
        gossip.broadcastHeartbeat(identity.getNodeId(), term);
    }

    private void resetElectionTimeout() {
        int range = Math.max(1, electionTimeoutMax - electionTimeoutMin);
        electionTimeout = electionTimeoutMin + random.nextInt(range);
    }
}