package com.defenderlink.mesh.service;

import com.defenderlink.mesh.identity.NodeIdentity;
import com.defenderlink.mesh.ledger.consensus.RaftConsensus;
import com.defenderlink.mesh.ledger.model.LedgerEntry;
import com.defenderlink.mesh.ledger.model.LedgerEntry.*;
import com.defenderlink.mesh.ledger.store.LedgerStore;
import com.defenderlink.mesh.ledger.store.LedgerStore.ServiceRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service registry — the ZTA control plane.
 *
 * Nodes expose local services (IP+port) to specific peers via the ledger.
 * The ledger entry is signed by the owning node's Ed25519 key.
 * No JWT, no cert, no token — just cryptographic proof of authorship.
 *
 * Example:
 *   serviceRegistry.exposeService("postgres-prod", "tcp", "127.0.0.1:5432",
 *       List.of(nodeB_id, nodeC_id));
 *
 * After this, NodeB and NodeC can see "postgres-prod" in their local ledger
 * and create tunnels to reach it. Other nodes cannot.
 */
@ApplicationScoped
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    @Inject
    NodeIdentity identity;

    @Inject
    LedgerStore ledger;

    @Inject
    RaftConsensus raft;

    // Auto-incrementing port for intercept proxy
    private final AtomicInteger nextProxyPort = new AtomicInteger(15000);

    /**
     * Expose a local service to specified peers.
     * Creates a SERVICE_EXPOSE ledger entry signed by this node.
     */
    public ServiceRecord exposeService(String serviceId, String protocol,
                                        String localBind, List<String> allowedNodes) {
        log.info("Exposing service '{}' ({}) at {} to {} peers",
                serviceId, protocol, localBind, allowedNodes.size());

        int assignedPort = nextProxyPort.getAndIncrement();

        ServiceExpose entry = new ServiceExpose(
                identity.getNodeId(),
                serviceId,
                protocol,
                localBind,
                assignedPort,
                allowedNodes,
                ServicePolicy.defaults(),
                Instant.now(),
                identity.sign(buildServiceSignable(serviceId, localBind, allowedNodes))
        );

        raft.submitEntry(entry);

        return new ServiceRecord(serviceId, identity.getNodeId(), protocol, localBind,
                assignedPort, allowedNodes, ServicePolicy.defaults(), Instant.now(), true);
    }

    /**
     * Revoke a service, removing it from the mesh.
     */
    public void revokeService(String serviceId, String reason) {
        log.info("Revoking service '{}': {}", serviceId, reason);

        ServiceRevoke entry = new ServiceRevoke(
                identity.getNodeId(),
                serviceId,
                reason,
                Instant.now(),
                identity.sign((serviceId + reason).getBytes())
        );

        raft.submitEntry(entry);
    }

    /**
     * Update allowed peers for an existing service.
     */
    public void updateServiceAccess(String serviceId, List<String> allowedNodes) {
        log.info("Updating access for '{}': {} peers", serviceId, allowedNodes.size());

        ServiceUpdate entry = new ServiceUpdate(
                identity.getNodeId(),
                serviceId,
                allowedNodes,
                ServicePolicy.defaults(),
                Instant.now(),
                identity.sign(buildServiceSignable(serviceId, "", allowedNodes))
        );

        raft.submitEntry(entry);
    }

    /**
     * Get all services this node is authorized to access.
     */
    public List<ServiceRecord> getAccessibleServices() {
        return ledger.getServicesForNode(identity.getNodeId());
    }

    /**
     * Get all services this node is exposing.
     */
    public List<ServiceRecord> getExposedServices() {
        return ledger.getServicesByOwner(identity.getNodeId());
    }

    /**
     * Lookup a specific service by ID.
     */
    public ServiceRecord getService(String serviceId) {
        return ledger.getService(serviceId)
                .filter(ServiceRecord::active)
                .orElse(null);
    }

    private byte[] buildServiceSignable(String serviceId, String localBind,
                                         List<String> allowedNodes) {
        String data = serviceId + localBind + String.join(",", allowedNodes);
        return data.getBytes();
    }
}
