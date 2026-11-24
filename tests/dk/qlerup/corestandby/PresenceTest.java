package dk.qlerup.corestandby;

public final class PresenceTest {
    private static void expect(Presence.Event expected, Presence.Event actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void expectSleep(boolean expected, Presence presence, boolean interactive) {
        if (presence.shouldSleep(interactive) != expected) {
            throw new AssertionError("Expected shouldSleep to be " + expected);
        }
    }

    public static void main(String[] args) {
        if (Presence.INTERVAL_MS != 30_000) throw new AssertionError("Expected 30-second polling");
        Presence presence = new Presence();
        for (int index = 0; index < 5; index++) {
            expect(Presence.Event.NONE, presence.sample(false));
        }
        expectSleep(false, presence, true);
        expect(Presence.Event.CONNECTED, presence.sample(true));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(true));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expectSleep(false, presence, true);
        expect(Presence.Event.LOST, presence.sample(false));
        expectSleep(true, presence, true);
        for (int index = 0; index < 3; index++) {
            expectSleep(false, presence, false);
            expect(Presence.Event.NONE, presence.sample(false));
            expectSleep(true, presence, true);
        }
        expect(Presence.Event.CONNECTED, presence.sample(true));
        expectSleep(false, presence, true);

        Presence restarted = new Presence();
        restarted.restore(false);
        expectSleep(true, restarted, true);
        expect(Presence.Event.CONNECTED, restarted.sample(true));
        expectSleep(false, restarted, true);
        restarted.restore(true);
        expect(Presence.Event.NONE, restarted.sample(false));
        expectSleep(false, restarted, true);
        // Re-enabling monitoring requires a fresh reply, even when a wake is pending.
        Presence reenabled = new Presence();
        reenabled.sample(false);
        if (reenabled.shouldRestoreScreen(false, true)) throw new AssertionError("Offline wake");
        reenabled.sample(true);
        if (!reenabled.shouldRestoreScreen(true, true)) throw new AssertionError("Lost pending wake");
        expect(Presence.Event.NONE, reenabled.sample(true));
        if (!reenabled.shouldRestoreScreen(true, true)) throw new AssertionError("No wake retry");
        if (reenabled.shouldRestoreScreen(true, false)) throw new AssertionError("Unowned sleep");
        System.out.println("Presence tests passed");
    }
}
