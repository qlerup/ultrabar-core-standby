package dk.qlerup.corestandby;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RecoveryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            disableStandby(context);
        }
    }

    static void disableStandby(Context context) {
        StandbyService.pause(context);
        DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
        if (policy.isAdminActive(SettingsState.admin(context))) {
            policy.removeActiveAdmin(SettingsState.admin(context));
        }
    }
}
