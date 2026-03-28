package com.defenderlink.mesh.ledger.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;

/**
 * All ledger record types. Each entry is signed by the authoring node
 * and appended to the Merkle chain. Once written, entries are immutable.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LedgerEntry.NodeRegister.class, name = "NODE_REGISTER"),
        @JsonSubTypes.Type(value = LedgerEntry.NodeDeregister.class, name = "NODE_DEREGISTER"),
        @JsonSubTypes.Type(value = LedgerEntry.NodeUpdate.class, name = "NODE_UPDATE"),
        @JsonSubTypes.Type(value = LedgerEntry.ServiceExpose.class, name = "SERVICE_EXPOSE"),
        @JsonSubTypes.Type(value = LedgerEntry.ServiceRevoke.class, name = "SERVICE_REVOKE"),
        @JsonSubTypes.Type(value = LedgerEntry.ServiceUpdate.class, name = "SERVICE_UPDATE"),
})
public sealed interface LedgerEntry {

    String type();
    String authorNodeId();
    Instant timestamp();
    byte[] signature();

    /**
     * A node announces itself to the mesh.
     * Contains everything other nodes need to reach it and establish tunnels.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NodeRegister(
            String authorNodeId,
            String wireguardPubkey,       // Curve25519 base64
            List<String> endpoints,        // ["192.168.5.50:51820", "10.0.0.5:51820"]
            List<String> capabilities,     // ["tunnel", "relay"]
            String displayName,            // human-readable label
            Instant timestamp,
            byte[] signature
    ) implements LedgerEntry {
        @Override public String type() { return "NODE_REGISTER"; }
    }

    /**
     * A node leaves the mesh gracefully.
     */
    record NodeDeregister(
            String authorNodeId,
            String reason,
            Instant timestamp,
            byte[] signature
    ) implements LedgerEntry {
        @Override public String type() { return "NODE_DEREGISTER"; }
    }

    /**
     * A node updates its reachable endpoints (IP change, new interface, etc.)
     */
    record NodeUpdate(
            String authorNodeId,
            List<String> endpoints,
            List<String> capabilities,
            Instant timestamp,
            byte[] signature
    ) implements LedgerEntry {
        @Override public String type() { return "NODE_UPDATE"; }
    }

    /**
     * A node exposes a local service to specific peers.
     * This is the core ZTA primitive: explicit, per-service, per-peer authorization.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ServiceExpose(
            String authorNodeId,
            String serviceId,              // unique service name (e.g. "postgres-prod")
            String protocol,               // "tcp" or "udp"
            String localBind,              // where the service actually runs (127.0.0.1:5432)
            int assignedPort,              // port peers will connect to on their local intercept
            List<String> allowedNodes,     // Ed25519 node IDs authorized to access this service
            ServicePolicy policy,
            Instant timestamp,
            byte[] signature
    ) implements LedgerEntry {
        @Override public String type() { return "SERVICE_EXPOSE"; }
    }

    /**
     * Revoke access to a previously exposed service.
     */
    record ServiceRevoke(
            String authorNodeId,
            String serviceId,
            String reason,
            Instant timestamp,
            byte[] signature
    ) implements LedgerEntry {
        @Override public String type() { return "SERVICE_REVOKE"; }
    }

    /**
     * Update allowed nodes for an existing service (add/remove peers).
     */
    record ServiceUpdate(
            String authorNodeId,
            String serviceId,
            List<String> allowedNodes,
            ServicePolicy policy,
            Instant timestamp,
            byte[] signature
    ) implements LedgerEntry {
        @Override public String type() { return "SERVICE_UPDATE"; }
    }

    /**
     * Policy constraints for a service.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ServicePolicy(
            boolean encrypted,             // always true for ZTA, but explicit
            String maxBandwidth,           // e.g. "100mbps" (enforced at WireGuard level)
            Integer maxConcurrentConns,
            Instant expiresAt              // optional TTL for temporary access
    ) {
        public static ServicePolicy defaults() {
            return new ServicePolicy(true, null, null, null);
        }
    }
}
