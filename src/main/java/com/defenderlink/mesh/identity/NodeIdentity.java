package com.defenderlink.mesh.identity;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Self-sovereign node identity based on Ed25519 keypair.
 *
 * The Ed25519 public key IS the node identity — no certificates,
 * no CA, no external authority. The ledger tracks which public keys
 * are registered members of the mesh.
 *
 * Ed25519 was chosen because:
 * - Deterministic signatures (no k-nonce issues like ECDSA)
 * - Small keys (32 bytes) and signatures (64 bytes)
 * - Fast verification (important for ledger block validation)
 * - Curve25519 (WireGuard) and Ed25519 share the same curve —
 *   trivial conversion for key reuse in Phase 2
 */
@ApplicationScoped
@Startup
public class NodeIdentity {

    private static final Logger log = LoggerFactory.getLogger(NodeIdentity.class);
    private static final String PRIV_KEY_FILE = "node.key";
    private static final String PUB_KEY_FILE = "node.pub";

    @ConfigProperty(name = "mesh.data-dir")
    String dataDir;

    private Ed25519PrivateKeyParameters privateKey;
    private Ed25519PublicKeyParameters publicKey;
    private String nodeId; // hex-encoded public key (64 chars)

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @PostConstruct
    void init() {
        try {
            Path keyDir = Path.of(dataDir, "identity");
            Files.createDirectories(keyDir);

            Path privPath = keyDir.resolve(PRIV_KEY_FILE);
            Path pubPath = keyDir.resolve(PUB_KEY_FILE);

            if (Files.exists(privPath) && Files.exists(pubPath)) {
                loadKeys(privPath, pubPath);
                log.info("Loaded existing node identity: {}", shortId());
            } else {
                generateKeys(privPath, pubPath);
                log.info("Generated new node identity: {}", shortId());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize node identity", e);
        }
    }

    // --- Public API ---

    /** Full node ID: hex-encoded Ed25519 public key (64 hex chars) */
    public String getNodeId() {
        return nodeId;
    }

    /** Short display ID: first 16 hex chars */
    public String shortId() {
        return nodeId.substring(0, 16);
    }

    /** Raw 32-byte public key */
    public byte[] getPublicKeyBytes() {
        return publicKey.getEncoded();
    }

    /** Base64-encoded public key (for ledger entries and WireGuard) */
    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Sign arbitrary data with this node's Ed25519 private key.
     * Used for: ledger entries, block proposals, service registrations.
     */
    public byte[] sign(byte[] data) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    /**
     * Verify a signature against a public key.
     * Used for: validating ledger entries from other nodes.
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] publicKeyBytes) {
        try {
            Ed25519PublicKeyParameters pubKey = new Ed25519PublicKeyParameters(publicKeyBytes, 0);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, pubKey);
            verifier.update(data, 0, data.length);
            return verifier.verifySignature(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** Verify using this node's own public key */
    public boolean verifySelf(byte[] data, byte[] signature) {
        return verify(data, signature, publicKey.getEncoded());
    }

    // --- Key Management ---

    private void generateKeys(Path privPath, Path pubPath) throws IOException {
        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();
        generator.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();
        publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
        nodeId = HexFormat.of().formatHex(publicKey.getEncoded());

        // Save keys (private key file permissions should be 600 in production)
        Files.write(privPath, privateKey.getEncoded());
        Files.write(pubPath, publicKey.getEncoded());

        // Restrict private key permissions
        privPath.toFile().setReadable(false, false);
        privPath.toFile().setReadable(true, true);
        privPath.toFile().setWritable(false, false);
        privPath.toFile().setWritable(true, true);
    }

    private void loadKeys(Path privPath, Path pubPath) throws IOException {
        byte[] privBytes = Files.readAllBytes(privPath);
        byte[] pubBytes = Files.readAllBytes(pubPath);

        privateKey = new Ed25519PrivateKeyParameters(privBytes, 0);
        publicKey = new Ed25519PublicKeyParameters(pubBytes, 0);
        nodeId = HexFormat.of().formatHex(publicKey.getEncoded());
    }
}
