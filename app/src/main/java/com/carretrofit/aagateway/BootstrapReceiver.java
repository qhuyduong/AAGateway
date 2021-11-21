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
                && getSystemProperty("persist.sys.aa.wireless").equals("true")) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.startService(relayIntent);
            Intent rfcommIntent = new Intent(context, RfcommService.class);
            context.startService(rfcommIntent);
            Intent hotspotIntent = new Intent(context, HotspotService.class);
            context.startService(hotspotIntent);
        } else if (action.equals("com.carretrofit.aagateway.action.STOP")
                && getSystemProperty("persist.sys.aa.wireless").equals("false")) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.stopService(relayIntent);
            Intent rfcommIntent = new Intent(context, RfcommService.class);
            context.stopService(rfcommIntent);
            Intent hotspotIntent = new Intent(context, HotspotService.class);
            context.stopService(hotspotIntent);
        }
    }

    private String getSystemProperty(String key) {
        String value = null;
        try {
            Class c = Class.forName("android.os.SystemProperties");
            value = (String) c.getMethod("get", String.class).invoke(c, key);
        } catch (Throwable ignored) {
        }

        return value;
    }
}
