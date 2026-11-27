package dk.qlerup.corestandby;

public final class PresenceTest {
    private static void expect(Presence.Event expected, Presence.Event actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static void expectOffline(boolean expected, Presence presence) {
        if (presence.offline() != expected) {
            throw new AssertionError("Expected offline to be " + expected);
        }
    }

    public static void main(String[] args) {
        if (Presence.INTERVAL_MS != 30_000) throw new AssertionError("Expected 30-second polling");
        if (Presence.OFFLINE_VIEW_MS != 30_000) throw new AssertionError("Expected a 30-second touch wake window");
        Presence presence = new Presence();
        for (int index = 0; index < 5; index++) {
            expect(Presence.Event.NONE, presence.sample(false));
        }
        expectOffline(false, presence);
        expect(Presence.Event.CONNECTED, presence.sample(true));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(true));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expectOffline(false, presence);
        expect(Presence.Event.LOST, presence.sample(false));
        expectOffline(true, presence);
        for (int index = 0; index < 3; index++) {
            expect(Presence.Event.NONE, presence.sample(false));
            expectOffline(true, presence);
        }
        expect(Presence.Event.CONNECTED, presence.sample(true));
        expectOffline(false, presence);
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.LOST, presence.sample(false));

        // Re-enabling monitoring requires a fresh reply, even when a wake is pending.
        Presence reenabled = new Presence();
        for (int index = 0; index < 5; index++) {
            expect(Presence.Event.NONE, reenabled.sample(false));
        }
        expectOffline(false, reenabled);
        if (reenabled.shouldRestoreScreen(false, true)) throw new AssertionError("Offline wake");
        reenabled.sample(true);
        if (!reenabled.shouldRestoreScreen(true, true)) throw new AssertionError("Lost pending wake");
        expect(Presence.Event.NONE, reenabled.sample(true));
        if (!reenabled.shouldRestoreScreen(true, true)) throw new AssertionError("No wake retry");
        if (reenabled.shouldRestoreScreen(true, false)) throw new AssertionError("Unowned sleep");
        System.out.println("Presence tests passed");
    }
}
