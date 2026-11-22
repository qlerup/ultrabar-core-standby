package dk.qlerup.corestandby;

final class Presence {
    enum Event { NONE, CONNECTED, LOST }
    private static final long LOSS_GRACE_MS = 20_000;
    private boolean connected;
    private long lastReply;

    Event sample(boolean reply, long now) {
        if (reply) {
            lastReply = now;
            if (!connected) {
                connected = true;
                return Event.CONNECTED;
            }
        } else if (connected && now - lastReply >= LOSS_GRACE_MS) {
            connected = false;
            return Event.LOST;
        }
        return Event.NONE;
    }
}
