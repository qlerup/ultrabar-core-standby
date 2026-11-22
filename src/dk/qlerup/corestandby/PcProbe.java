package dk.qlerup.corestandby;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class PcProbe implements Probe {
    private static final int PORT = 47431;
    private static final int NONCE_BYTES = 32;
    private static final int MAC_BYTES = 32;
    private final SecureRandom random = new SecureRandom();
    private final Inet4Address address;
    private final Mac mac;

    PcProbe(String ip, String code) throws GeneralSecurityException, IOException {
        String[] parts = ip.split("\\.", -1);
        byte[] octets = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            octets[index] = (byte) Integer.parseInt(parts[index]);
        }
        address = (Inet4Address) Inet4Address.getByAddress(octets);
        byte[] key = new byte[16];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) Integer.parseInt(code.substring(index * 2, index * 2 + 2), 16);
        }
        mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
    }

    @Override
    public boolean ping() throws IOException {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] expected = mac.doFinal(nonce);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(address, PORT);
            socket.send(new DatagramPacket(nonce, nonce.length));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            byte[] response = new byte[MAC_BYTES + 1];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                socket.setSoTimeout((int) TimeUnit.NANOSECONDS.toMillis(remaining) + 1);
                packet.setLength(response.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException timeout) {
                    return false;
                }
                if (packet.getLength() == MAC_BYTES) {
                    byte[] reply = java.util.Arrays.copyOf(response, MAC_BYTES);
                    if (MessageDigest.isEqual(expected, reply)) return true;
                }
            }
        }
    }
}
