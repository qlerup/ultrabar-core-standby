package dk.qlerup.corestandby;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public final class SetupActivity extends Activity {
    private EditText address;
    private EditText code;
    private TextView status;
    private Button toggle;
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = SettingsState.preferences(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        scroll.addView(panel);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("PC Standby");
        title.setTextSize(24);
        panel.addView(title);
        status = new TextView(this);
        panel.addView(status);
        LinearLayout fields = row(panel);
        address = field(fields, "Windows PC IPv4 address", prefs.getString("ip", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code = field(fields, "Windows pairing code (32 hexadecimal characters)", prefs.getString("code", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout permissions = row(panel);
        button(permissions, "Allow changes to screen timeout", () -> startActivity(
                new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
        button(permissions, "Allow PC Standby to put Core to sleep", () -> {
            if (getSystemService(DevicePolicyManager.class).isAdminActive(SettingsState.admin(this))) {
                status.setText("Sleep permission is already enabled.");
                return;
            }
            Intent request = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            request.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, SettingsState.admin(this));
            request.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Allows PC Standby to turn off the Core screen when the paired Windows app stops answering.");
            startActivity(request);
        });
        LinearLayout controls = row(panel);
        toggle = button(controls, "", this::toggle);
        button(controls, "Remove sleep permission", () -> {
            StandbyService.pause(this);
            getSystemService(DevicePolicyManager.class).removeActiveAdmin(SettingsState.admin(this));
            refresh();
        });
        panel.setFocusableInTouchMode(true);
        panel.requestFocus();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private LinearLayout row(LinearLayout panel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(getResources().getConfiguration().screenWidthDp >= 750
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        panel.addView(row);
        return row;
    }

    private void addControl(LinearLayout row, View control) {
        if (row.getOrientation() == LinearLayout.HORIZONTAL) {
            row.addView(control, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        } else {
            row.addView(control);
        }
    }

    private EditText field(LinearLayout panel, String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint(hint);
        field.setInputType(inputType);
        field.setText(value);
        addControl(panel, field);
        return field;
    }

    private Button button(LinearLayout panel, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(view -> action.run());
        addControl(panel, button);
        return button;
    }

    private void refresh() {
        boolean enabled = prefs.getBoolean("enabled", false);
        address.setEnabled(!enabled);
        code.setEnabled(!enabled);
        toggle.setText(enabled ? "Pause monitoring" : "Start monitoring");
        if (enabled) {
            status.setText("Monitoring is enabled. Open the notification to see the connection status.");
        } else if (SettingsState.restoreTimeout(this)) {
            status.setText("Enter the IPv4 address and pairing code shown by Windows setup.");
        } else {
            status.setText("Allow screen timeout changes again so PC Standby can restore your previous timeout.");
        }
    }

    private void toggle() {
        if (prefs.getBoolean("enabled", false)) {
            StandbyService.pause(this);
            refresh();
            return;
        }
        String ip = address.getText().toString().trim();
        String secret = code.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!validAddress(ip)) {
            address.setError("Enter the Windows PC's IPv4 address.");
            return;
        }
        if (!secret.matches("[0-9a-f]{32}")) {
            code.setError("Enter the 32-character pairing code from Windows setup.");
            return;
        }
        if (!SettingsState.ready(this)) {
            status.setText("Grant both permissions before starting monitoring.");
            return;
        }
        if (!prefs.edit().putString("ip", ip).putString("code", secret).putBoolean("enabled", true).commit()) {
            status.setText("Could not save the setup.");
            return;
        }
        startForegroundService(new Intent(this, StandbyService.class));
        refresh();
    }

    private static boolean validAddress(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return false;
        }
        int first = Integer.parseInt(parts[0]);
        return first > 0 && first < 224 && first != 127;
    }
}
