package dk.qlerup.corestandby;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

final class SettingsState {
    private static final String TIMEOUT = Settings.System.SCREEN_OFF_TIMEOUT;
    private static final String SAVED = "previousTimeout";
    private static final String NEVER = Integer.toString(Integer.MAX_VALUE);

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("standby", Context.MODE_PRIVATE);
    }

    static ComponentName admin(Context context) {
        return new ComponentName(context, StandbyAdmin.class);
    }

    static boolean ready(Context context) {
        return Settings.System.canWrite(context)
                && context.getSystemService(DevicePolicyManager.class).isAdminActive(admin(context));
    }

    static boolean takeTimeout(Context context) {
        SharedPreferences prefs = preferences(context);
        String current = Settings.System.getString(context.getContentResolver(), TIMEOUT);
        if (NEVER.equals(current)) return true;
        if (!prefs.edit().putString(SAVED, current == null ? "" : current).commit()) return false;
        return Settings.System.putString(context.getContentResolver(), TIMEOUT, NEVER)
                && NEVER.equals(Settings.System.getString(context.getContentResolver(), TIMEOUT));
    }

    static boolean restoreTimeout(Context context) {
        SharedPreferences prefs = preferences(context);
        if (!prefs.contains(SAVED)) return true;
        if (NEVER.equals(Settings.System.getString(context.getContentResolver(), TIMEOUT))) {
            if (!Settings.System.canWrite(context)) return false;
            String saved = prefs.getString(SAVED, "");
            if (!Settings.System.putString(context.getContentResolver(), TIMEOUT,
                    saved.isEmpty() ? null : saved)) return false;
        }
        return prefs.edit().remove(SAVED).commit();
    }

    private SettingsState() {}
}
