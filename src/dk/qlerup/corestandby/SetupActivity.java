package dk.qlerup.corestandby;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        RecoveryReceiver.disableStandby(this);
        TextView status = new TextView(this);
        status.setText("PC Standby is temporarily disabled.");
        setContentView(status);
    }
}
