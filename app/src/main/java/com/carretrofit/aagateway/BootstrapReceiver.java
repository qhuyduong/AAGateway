package com.carretrofit.aagateway;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import com.symbio.scc.SccRfComm;

import java.lang.reflect.Method;

public class BootstrapReceiver extends BroadcastReceiver {
    private static final String TAG = "AAGatewayBootstrapReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent relayIntent = new Intent(context, RelayService.class);
            context.startService(relayIntent);
            BluetoothAdapter.getDefaultAdapter().disable();
            enableHotspot(context);
        } else if (action.equals("android.net.wifi.WIFI_AP_STATE_CHANGED")) {
            int wifiState =
                    intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if (wifiState % 10 == WifiManager.WIFI_STATE_ENABLED) {
                BluetoothAdapter.getDefaultAdapter().enable();
            }
        } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device =
                    (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            Log.d(TAG, "Device connected " + device);
            SccRfComm sccRfComm = new SccRfComm(new Handler());
            sccRfComm.connect(device);
        }
    }

    private void enableHotspot(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = BluetoothAdapter.getDefaultAdapter().getName();
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
            Log.e(TAG, e.getMessage());
        }
    }
}
