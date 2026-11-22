package dk.qlerup.corestandby;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StudioProbe implements Probe {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final byte[] key;
    private final String pairing;

    StudioProbe(Context context) throws IOException, GeneralSecurityException {
        File config = config(context);
        if (config.length() != 32) throw new IOException("Invalid Studio pairing");
        key = Files.readAllBytes(config.toPath());
        pairing = hex(MessageDigest.getInstance("SHA-256").digest(key)).substring(0, 32);
    }

    static File config(Context context) {
        return new File(context.getFilesDir(), "studio-key");
    }

    @Override
    public boolean ping() throws IOException {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        URL url = new URL("http://127.0.0.1:47431/pc-standby/" + pairing + "/" + hex(nonce));
        HttpURLConnection request = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        request.setConnectTimeout(1000);
        request.setReadTimeout(1000);
        request.setInstanceFollowRedirects(false);
        request.setUseCaches(false);
        try {
            if (request.getResponseCode() != 200 || request.getContentLength() != 32) return false;
            byte[] reply = new byte[32];
            try (InputStream stream = request.getInputStream()) {
                new java.io.DataInputStream(stream).readFully(reply);
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(nonce), reply);
        } catch (GeneralSecurityException error) {
            throw new IOException("Could not verify Studio presence", error);
        } finally {
            request.disconnect();
        }
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 15];
        }
        return new String(out);
    }
}
