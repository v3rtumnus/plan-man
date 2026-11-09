package at.v3rtumnus.planman.service.notification;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push with the JDK only: message encryption (RFC 8291, aes128gcm) and the VAPID
 * JWT (RFC 8292, ES256). No maintained Java Web Push library was found (2026-11), and the
 * whole protocol is ECDH + HKDF + AES-GCM + ECDSA.
 */
public final class WebPushCrypto {

    /** One record holds the whole message; the payload must stay below this minus 17 bytes. */
    static final int RECORD_SIZE = 4096;

    private static final ECParameterSpec P256 = p256();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushCrypto() {
    }

    /** Encrypts a payload for one subscription. Returns the request body (header + ciphertext). */
    public static byte[] encrypt(byte[] payload, String uaPublicB64, String authSecretB64) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(P256, RANDOM);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            return encrypt(payload, decode(uaPublicB64), decode(authSecretB64), generator.generateKeyPair(), salt);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Web Push encryption failed", e);
        }
    }

    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        if (payload.length > RECORD_SIZE - 17) {
            throw new IllegalArgumentException("Push payload too large: " + payload.length + " bytes");
        }
        byte[] asPublic = encodePoint((ECPublicKey) asKeys.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asKeys.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        // RFC 8291 § 3.3/3.4: IKM from the auth secret, then CEK and nonce from the salt.
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(concat(payload, new byte[]{2})); // 0x02 = last record, no padding

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** VAPID {@code Authorization} header value for a push service origin. */
    public static String vapidAuthorization(String audience, String subject, long expiresEpochSeconds,
                                            String publicKeyB64, PrivateKey privateKey) {
        try {
            String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresEpochSeconds
                    + ",\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(privateKey);
            signature.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
            String jwt = header + "." + claims + "." + b64(signature.sign());
            return "vapid t=" + jwt + ", k=" + publicKeyB64;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID signing failed", e);
        }
    }

    /** The VAPID private key from its raw 32-byte scalar (base64url, as web-push tools print it). */
    public static PrivateKey privateKey(String rawB64) {
        byte[] raw = decode(rawB64);
        if (raw.length != 32) {
            throw new IllegalArgumentException("VAPID private key must be 32 bytes, got " + raw.length);
        }
        try {
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid VAPID private key", e);
        }
    }

    /** A P-256 public key from its 65-byte uncompressed encoding. */
    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new InvalidKeyException("Expected an uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    static byte[] encodePoint(ECPublicKey key) {
        return concat(new byte[]{4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    /** True if the raw private scalar belongs to the uncompressed public key (config check). */
    public static boolean keysMatch(String publicB64, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey);
            byte[] probe = "vapid-check".getBytes(StandardCharsets.US_ASCII);
            signer.update(probe);
            byte[] sig = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey(decode(publicB64)));
            verifier.update(probe);
            return verifier.verify(sig);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** HKDF-SHA256 (RFC 5869) for outputs up to 32 bytes: one Expand block is enough. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    static byte[] decode(String b64) {
        return B64URL_DECODER.decode(b64.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static String b64(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }

    static ECParameterSpec p256Spec() {
        return P256;
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 not available", e);
        }
    }
}
