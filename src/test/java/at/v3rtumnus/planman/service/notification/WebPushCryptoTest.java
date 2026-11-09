package at.v3rtumnus.planman.service.notification;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushCryptoTest {

    /** Decrypts like a browser does (RFC 8291 receiver side) with the subscription's private key. */
    private static byte[] decrypt(byte[] body, PushTestKeys ua, byte[] authSecret) throws Exception {
        ByteBuffer header = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        header.get(salt);
        int recordSize = header.getInt();
        int keyLength = header.get() & 0xff;
        byte[] asPublic = new byte[keyLength];
        header.get(asPublic);
        assertThat(recordSize).isEqualTo(4096);
        assertThat(keyLength).isEqualTo(65);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ua.pair.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] ecdh = agreement.generateSecret();

        byte[] uaPublic = WebPushCrypto.decode(ua.publicB64);
        byte[] keyInfo = ByteBuffer.allocate(14 + 65 + 65)
                .put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII)).put(uaPublic).put(asPublic).array();
        byte[] ikm = WebPushCrypto.hkdf(authSecret, ecdh, keyInfo, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(Arrays.copyOfRange(body, header.position(), body.length));
        assertThat(plain[plain.length - 1]).as("last-record delimiter").isEqualTo((byte) 2);
        return Arrays.copyOf(plain, plain.length - 1);
    }

    @Test
    void encrypt_roundTripsForTheSubscriber() throws Exception {
        PushTestKeys ua = PushTestKeys.generate();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        byte[] payload = "{\"title\":\"Budget knapp\",\"body\":\"Noch 240 €\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(payload, ua.publicB64, WebPushCrypto.b64(auth));

        assertThat(decrypt(body, ua, auth)).isEqualTo(payload);
    }

    @Test
    void encrypt_acceptsPaddedStandardBase64Keys() throws Exception {
        PushTestKeys ua = PushTestKeys.generate();
        byte[] auth = new byte[16];
        String standardPublic = Base64.getEncoder().encodeToString(WebPushCrypto.decode(ua.publicB64));

        byte[] body = WebPushCrypto.encrypt("x".getBytes(StandardCharsets.UTF_8), standardPublic,
                Base64.getEncoder().encodeToString(auth));

        assertThat(decrypt(body, ua, auth)).isEqualTo("x".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encrypt_rejectsPayloadAboveOneRecord() {
        PushTestKeys ua = PushTestKeys.generate();
        byte[] tooLarge = new byte[WebPushCrypto.RECORD_SIZE];

        assertThatThrownBy(() -> WebPushCrypto.encrypt(tooLarge, ua.publicB64, WebPushCrypto.b64(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vapidAuthorization_isAnEs256JwtSignedByTheServerKey() throws Exception {
        PushTestKeys server = PushTestKeys.generate();

        String header = WebPushCrypto.vapidAuthorization("https://fcm.googleapis.com", "https://plan-man.example",
                1_800_000_000L, server.publicB64, WebPushCrypto.privateKey(server.privateB64));

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + server.publicB64);
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(new String(WebPushCrypto.decode(parts[0]), StandardCharsets.UTF_8)).contains("\"alg\":\"ES256\"");
        assertThat(new String(WebPushCrypto.decode(parts[1]), StandardCharsets.UTF_8))
                .isEqualTo("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1800000000,\"sub\":\"https://plan-man.example\"}");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(server.pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(WebPushCrypto.decode(parts[2]))).isTrue();
    }

    @Test
    void keysMatch_onlyForTheSamePair() {
        PushTestKeys a = PushTestKeys.generate();
        PushTestKeys b = PushTestKeys.generate();

        assertThat(WebPushCrypto.keysMatch(a.publicB64, WebPushCrypto.privateKey(a.privateB64))).isTrue();
        assertThat(WebPushCrypto.keysMatch(b.publicB64, WebPushCrypto.privateKey(a.privateB64))).isFalse();
        assertThat(WebPushCrypto.keysMatch("not-a-key", WebPushCrypto.privateKey(a.privateB64))).isFalse();
    }

    @Test
    void publicKey_rejectsCompressedOrShortPoints() {
        assertThatThrownBy(() -> WebPushCrypto.publicKey(new byte[33])).isInstanceOf(java.security.InvalidKeyException.class);
        byte[] wrongPrefix = new byte[65];
        wrongPrefix[0] = 3;
        assertThatThrownBy(() -> WebPushCrypto.publicKey(wrongPrefix)).isInstanceOf(java.security.InvalidKeyException.class);
    }

    @Test
    void privateKey_rejectsGarbage() {
        assertThatThrownBy(() -> WebPushCrypto.privateKey("")).isInstanceOf(IllegalArgumentException.class);
    }
}
