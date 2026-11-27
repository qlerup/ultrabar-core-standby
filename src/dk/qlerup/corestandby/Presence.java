package dk.qlerup.corestandby;

final class Presence {
    enum Event { NONE, CONNECTED, LOST }
    static final int INTERVAL_MS = 30_000;
    static final int OFFLINE_VIEW_MS = 30_000;
    private static final int MISSED_LIMIT = 3;
    private enum State { WAITING, CONNECTED, OFFLINE }
    private State state = State.WAITING;
    private int missed;

    boolean offline() {
        return state == State.OFFLINE;
    }

    boolean shouldRestoreScreen(boolean authenticated, boolean slept) {
        return authenticated && slept && state == State.CONNECTED;
    }

    Event sample(boolean reply) {
        if (reply) {
            missed = 0;
            if (state != State.CONNECTED) {
                state = State.CONNECTED;
                return Event.CONNECTED;
            }
        } else if (state == State.CONNECTED && ++missed == MISSED_LIMIT) {
            state = State.OFFLINE;
            return Event.LOST;
        }
        return Event.NONE;
    }
}
