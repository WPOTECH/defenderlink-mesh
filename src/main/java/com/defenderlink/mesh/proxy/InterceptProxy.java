package com.defenderlink.mesh.proxy;

import com.defenderlink.mesh.ledger.store.LedgerStore.ServiceRecord;
import com.defenderlink.mesh.tunnel.TunnelManager;
import com.defenderlink.mesh.tunnel.TunnelManager.ActiveTunnel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class InterceptProxy {

    private static final Logger log = LoggerFactory.getLogger(InterceptProxy.class);
    private static final int BUFFER_SIZE = 8192;

    @ConfigProperty(name = "mesh.proxy.bind-address", defaultValue = "127.0.0.1")
    String bindAddress;

    @ConfigProperty(name = "mesh.proxy.local-port-start", defaultValue = "14000")
    int localPortStart;

    @ConfigProperty(name = "mesh.proxy.local-port-range", defaultValue = "1000")
    int localPortRange;

    @Inject TunnelManager tunnelManager;

    private final Map<String, ProxyBinding> bindings    = new ConcurrentHashMap<>();
    private final Set<Integer>              freePorts   = ConcurrentHashMap.newKeySet();
    private final AtomicInteger             portCursor  = new AtomicInteger(0);

    // ── Primary entry point ──────────────────────────────────────────────────

    /**
     * Called from TunnelManager.ensureTunnel() after a successful negotiate.
     * Returns the local port apps should connect to.
     */
    public int startIntercept(String serviceId, String ownerTunnelIp, int egressPort) {
        ProxyBinding existing = bindings.get(serviceId);
        if (existing != null) {
            log.info("Intercept already active for '{}' on :{}", serviceId, existing.localPort);
            return existing.localPort;
        }

        int localPort = allocateLocalPort();
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), localPort));

            ProxyBinding b = new ProxyBinding(serviceId, localPort, ownerTunnelIp, egressPort, ss);
            bindings.put(serviceId, b);

            Thread.ofVirtual().name("intercept-" + serviceId)
                    .start(() -> acceptLoop(b));

            log.info("Intercept active: {}:{} → {}:{} for svc='{}'",
                    bindAddress, localPort, ownerTunnelIp, egressPort, serviceId);
            return localPort;

        } catch (IOException e) {
            freePorts.add(localPort);
            throw new RuntimeException("Intercept bind failed on port " + localPort, e);
        }
    }

    /** Legacy entry point — used by ConnectResource before tunnel coords are known. */
    public ProxyBinding startIntercept(ServiceRecord service) {
        ProxyBinding existing = bindings.get(service.serviceId());
        if (existing != null) return existing;

        ActiveTunnel tunnel = tunnelManager.getActiveTunnel(service.serviceId())
                .orElseThrow(() -> new IllegalStateException(
                        "No active tunnel for '" + service.serviceId() + "'"));

        startIntercept(service.serviceId(), tunnel.peerTunnelIp(), tunnel.egressPort());
        return bindings.get(service.serviceId());
    }

    public void stopIntercept(String serviceId) {
        ProxyBinding b = bindings.remove(serviceId);
        if (b == null) return;
        b.active = false;
        try { b.serverSocket.close(); } catch (IOException ignored) {}
        freePorts.add(b.localPort);
        log.info("Stopped intercept for '{}', freed port {}", serviceId, b.localPort);
    }

    public Map<String, ProxyBinding> getBindings() { return Map.copyOf(bindings); }

    // ── Port allocation ──────────────────────────────────────────────────────

    private int allocateLocalPort() {
        var it = freePorts.iterator();
        if (it.hasNext()) { int p = it.next(); it.remove(); return p; }

        int offset = portCursor.getAndIncrement();
        if (offset >= localPortRange) {
            compactDeadBindings();
            portCursor.addAndGet(-1);
            offset = portCursor.getAndIncrement();
            if (offset >= localPortRange)
                throw new IllegalStateException("Intercept port range exhausted — "
                        + "increase mesh.proxy.local-port-range (currently " + localPortRange + ")");
        }
        return localPortStart + offset;
    }

    private void compactDeadBindings() {
        bindings.entrySet().removeIf(e -> {
            if (e.getValue().serverSocket.isClosed()) {
                freePorts.add(e.getValue().localPort);
                return true;
            }
            return false;
        });
    }

    // ── Accept + forward loop ────────────────────────────────────────────────

    private void acceptLoop(ProxyBinding b) {
        while (b.active) {
            try {
                Socket client = b.serverSocket.accept();
                client.setTcpNoDelay(true);
                Thread.ofVirtual()
                        .name("intercept-conn-" + b.serviceId + "-" + client.getPort())
                        .start(() -> handleConnection(b, client));
            } catch (IOException e) {
                if (b.active) log.warn("Accept error for '{}': {}", b.serviceId, e.getMessage());
            }
        }
    }

    private void handleConnection(ProxyBinding b, Socket client) {
        String id = b.serviceId + ":" + client.getPort();
        try {
            // Re-verify tunnel is still up
            ActiveTunnel tunnel = tunnelManager.ensureTunnel(b.serviceId);
            if (!tunnel.isUp()) {
                log.warn("Tunnel down for [{}], dropping", id);
                client.close(); return;
            }
        } catch (Exception e) {
            log.warn("Tunnel unavailable for [{}]: {}", id, e.getMessage());
            silentClose(client); return;
        }

        try (client;
             Socket remote = new Socket()) {
            remote.setTcpNoDelay(true);
            remote.connect(new InetSocketAddress(b.ownerTunnelIp, b.egressPort), 5000);

            InputStream  ci = client.getInputStream();
            OutputStream co = client.getOutputStream();
            InputStream  ri = remote.getInputStream();
            OutputStream ro = remote.getOutputStream();

            Thread t1 = Thread.ofVirtual().start(() -> pipe(ci, ro, remote));
            Thread t2 = Thread.ofVirtual().start(() -> pipe(ri, co, client));
            t1.join(); t2.join();

        } catch (Exception e) {
            log.debug("Connection [{}] closed: {}", id, e.getMessage());
        }
    }

    private void pipe(InputStream in, OutputStream out, Socket closeTrigger) {
        byte[] buf = new byte[BUFFER_SIZE];
        try {
            int n;
            while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); out.flush(); }
        } catch (IOException ignored) {
        } finally { silentClose(closeTrigger); }
    }

    private static void silentClose(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }

    // ── Binding record ───────────────────────────────────────────────────────

    public static class ProxyBinding {
        public final String       serviceId;
        public final int          localPort;
        public final String       ownerTunnelIp;
        public final int          egressPort;
        public final ServerSocket serverSocket;
        public volatile boolean   active = true;

        public ProxyBinding(String serviceId, int localPort,
                            String ownerTunnelIp, int egressPort,
                            ServerSocket serverSocket) {
            this.serviceId     = serviceId;
            this.localPort     = localPort;
            this.ownerTunnelIp = ownerTunnelIp;
            this.egressPort    = egressPort;
            this.serverSocket  = serverSocket;
        }
    }
}