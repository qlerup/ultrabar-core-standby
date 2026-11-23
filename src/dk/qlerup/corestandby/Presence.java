package dk.qlerup.corestandby;

final class Presence {
    enum Event { NONE, CONNECTED, LOST }
    static final int INTERVAL_MS = 30_000;
    private static final int MISSED_LIMIT = 3;
    private enum State { WAITING, CONNECTED, OFFLINE }
    private State state = State.WAITING;
    private int missed;

    void restore(boolean connected) {
        state = connected ? State.CONNECTED : State.OFFLINE;
        missed = 0;
    }

    boolean shouldSleep(boolean interactive) {
        return state == State.OFFLINE && interactive;
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
