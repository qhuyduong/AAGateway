package com.carretrofit.aagateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootstrapReceiver extends BroadcastReceiver {
    private static final String TAG = "AAGatewayBootstrapReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if ((action.equals(Intent.ACTION_BOOT_COMPLETED)
                        || action.equals("com.carretrofit.aagateway.action.START"))
                && getSystemProperty("persist.sys.aa.wireless", "true").equals("true")) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.startService(relayIntent);
            Intent rfcommIntent = new Intent(context, RfcommService.class);
            context.startService(rfcommIntent);
        } else if (action.equals("com.carretrofit.aagateway.action.STOP")
                && getSystemProperty("persist.sys.aa.wireless", "true").equals("false")) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.stopService(relayIntent);
            Intent rfcommIntent = new Intent(context, RfcommService.class);
            context.stopService(rfcommIntent);
        }
    }

    public String getSystemProperty(String key, String defaultValue) {
        String value = null;
        try {
            Class c = Class.forName("android.os.SystemProperties");
            value = (String) c.getMethod("get", String.class).invoke(c, key);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return (value != null) ? value : defaultValue;
    }
}
