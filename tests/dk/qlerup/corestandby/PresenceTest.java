package dk.qlerup.corestandby;

public final class PresenceTest {
    private static void expect(Presence.Event expected, Presence.Event actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        Presence presence = new Presence();
        expect(Presence.Event.NONE, presence.sample(false, 0));
        expect(Presence.Event.NONE, presence.sample(false, 90_000));
        expect(Presence.Event.CONNECTED, presence.sample(true, 100_000));
        expect(Presence.Event.NONE, presence.sample(false, 119_999));
        expect(Presence.Event.NONE, presence.sample(true, 120_000));
        expect(Presence.Event.NONE, presence.sample(false, 139_999));
        expect(Presence.Event.LOST, presence.sample(false, 140_000));
        expect(Presence.Event.NONE, presence.sample(false, 150_000));
        expect(Presence.Event.CONNECTED, presence.sample(true, 155_000));
        expect(Presence.Event.LOST, presence.sample(false, 175_000));
        System.out.println("Presence tests passed");
    }
}
