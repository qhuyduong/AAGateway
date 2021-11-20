package com.carretrofit.aagateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootstrapReceiver extends BroadcastReceiver {
    private static final String TAG = "AAGatewayBootstrapReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.startService(relayIntent);
            Intent rfcommIntent = new Intent(context, RfcommService.class);
            context.startService(rfcommIntent);
        }
    }
}
