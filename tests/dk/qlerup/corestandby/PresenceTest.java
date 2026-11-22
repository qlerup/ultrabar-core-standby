package dk.qlerup.corestandby;

public final class PresenceTest {
    private static void expect(Presence.Event expected, Presence.Event actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        if (Presence.INTERVAL_MS != 30_000) throw new AssertionError("Expected 30-second polling");
        Presence presence = new Presence();
        for (int index = 0; index < 5; index++) {
            expect(Presence.Event.NONE, presence.sample(false));
        }
        expect(Presence.Event.CONNECTED, presence.sample(true));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(true));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.LOST, presence.sample(false));
        expect(Presence.Event.NONE, presence.sample(false));
        expect(Presence.Event.CONNECTED, presence.sample(true));
        System.out.println("Presence tests passed");
    }
}
