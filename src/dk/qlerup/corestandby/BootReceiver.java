package dk.qlerup.corestandby;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (SettingsState.preferences(context).getBoolean("enabled", false)) {
            context.startForegroundService(new Intent(context, StandbyService.class));
        } else {
            SettingsState.restoreTimeout(context);
        }
    }
}
