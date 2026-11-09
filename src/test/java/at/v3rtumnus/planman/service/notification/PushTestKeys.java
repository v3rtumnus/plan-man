package at.v3rtumnus.planman.service.notification;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/** Fresh P-256 key pairs encoded the way browsers and VAPID tools hand them over. */
final class PushTestKeys {

    final KeyPair pair;
    final String publicB64;
    final String privateB64;

    private PushTestKeys(KeyPair pair) {
        this.pair = pair;
        this.publicB64 = WebPushCrypto.b64(WebPushCrypto.encodePoint((ECPublicKey) pair.getPublic()));
        this.privateB64 = WebPushCrypto.b64(raw32(((ECPrivateKey) pair.getPrivate()).getS()));
    }

    static PushTestKeys generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(WebPushCrypto.p256Spec());
            return new PushTestKeys(generator.generateKeyPair());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] raw32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }
}
