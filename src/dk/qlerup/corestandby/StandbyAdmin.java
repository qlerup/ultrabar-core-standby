package dk.qlerup.corestandby;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public final class StandbyAdmin extends DeviceAdminReceiver {
    @Override
    public void onDisabled(Context context, Intent intent) {
        StandbyService.pause(context);
    }
}
