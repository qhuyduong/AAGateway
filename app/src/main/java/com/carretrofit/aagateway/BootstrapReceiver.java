package com.carretrofit.aagateway;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Method;

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
        } else if (action.equals("com.carretrofit.aagateway.action.STOP")
                && getSystemProperty("persist.sys.aa.wireless").equals("false")) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.stopService(relayIntent);
            Intent rfcommIntent = new Intent(context, RfcommService.class);
            context.stopService(rfcommIntent);
        } else if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
            String name = (String) intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
            Log.d(TAG, "Bluetooth local name changed to " + name);
            enableHotspot(context, name);
        }
    }

    private String getSystemProperty(String key) {
        String value = null;
        try {
            Class c = Class.forName("android.os.SystemProperties");
            value = (String) c.getMethod("get", String.class).invoke(c, key);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    private void enableHotspot(Context context, String ssid) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = ssid;
        wifiConfig.preSharedKey = Constants.WIFI_PASSWORD;
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
            e.printStackTrace();
        }
    }
}
