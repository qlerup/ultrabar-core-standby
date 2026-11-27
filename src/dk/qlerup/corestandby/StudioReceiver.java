package dk.qlerup.corestandby;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StudioReceiver extends BroadcastReceiver {
    private static final String ENABLE = "dk.qlerup.corestandby.ENABLE";
    private static final String DISABLE = "dk.qlerup.corestandby.DISABLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ENABLE.equals(intent.getAction())) {
            try {
                new StudioProbe(context);
                if (!SettingsState.ready(context)) return;
                if (!SettingsState.preferences(context).edit().putBoolean("enabled", true)
                        .putBoolean("studioConnected", false).remove("studioConfirmed")
                        .commit()) return;
                context.startForegroundService(new Intent(context, StandbyService.class));
                setResultCode(1);
            } catch (java.io.IOException | java.security.GeneralSecurityException error) {
                setResultCode(0);
            }
        } else if (DISABLE.equals(intent.getAction())) {
            if (StandbyService.pause(context)) setResultCode(1);
        }
    }
}
