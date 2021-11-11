package com.carretrofit.aagateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Method;

public class MyReceiver extends BroadcastReceiver {
    private static final String TAG = "AAGatewayReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "Received boot completed");
            Intent i = new Intent(context, MyService.class);
            context.startService(i);
        } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            int wifiState =
                    intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);

            if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                Log.d(TAG, "Enabling hotspot");
                enableHotspot(context);
            }
        }
    }

    private void enableHotspot(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = "Car Retrofit";
        wifiConfig.preSharedKey = "CarRetrofit";
        wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        try {
            wifiManager.setWifiEnabled(false);
            Method method =
                    wifiManager
                            .getClass()
                            .getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifiManager, wifiConfig, true);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
}
