package dk.qlerup.corestandby;

final class Presence {
    enum Event { NONE, CONNECTED, LOST }
    static final int INTERVAL_MS = 30_000;
    private static final int MISSED_LIMIT = 3;
    private boolean connected;
    private int missed;

    Event sample(boolean reply) {
        if (reply) {
            missed = 0;
            if (!connected) {
                connected = true;
                return Event.CONNECTED;
            }
        } else if (connected && ++missed == MISSED_LIMIT) {
            connected = false;
            return Event.LOST;
        }
        return Event.NONE;
    }
}
