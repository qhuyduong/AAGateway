package com.carretrofit.aagateway;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import com.carretrofit.aagateway.proto.WifiInfoRequestMessage;
import com.carretrofit.aagateway.proto.WifiSecurityResponseMessage;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

public class RfcommService extends Service {
    private static final String TAG = "AAGateWayRfcommService";
    private static final String AAW_NAME = "Android Auto Wireless";
    private static final UUID AAW_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");
    private static final UUID HSP_UUID = UUID.fromString("00001112-0000-1000-8000-00805f9b34fb");

    private BroadcastReceiver receiver =
            new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                        BluetoothDevice device =
                                (BluetoothDevice)
                                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Log.d(TAG, "Device connected " + device);

                        new Thread(new AAWListenerThread(device)).start();
                    }
                }
            };

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        Log.d(TAG, "Service destroyed");
    }

    private class AAWListenerThread implements Runnable {
        private BluetoothDevice device;
        private DataInputStream inputStream;
        private OutputStream outputStream;

        public AAWListenerThread(BluetoothDevice dev) {
            device = dev;
        }

        public void run() {
            BluetoothServerSocket serverSocket = null;
            BluetoothSocket socket = null;
            try {
                serverSocket =
                        BluetoothAdapter.getDefaultAdapter()
                                .listenUsingRfcommWithServiceRecord(AAW_NAME, AAW_UUID);
                socket = serverSocket.accept();
                serverSocket.close();

                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = socket.getOutputStream();
                Log.d(TAG, "Connecting to AAW on device " + device);
                connectToPhone(device);
            } catch (Exception e) {
                Log.e(TAG, "AAListener - error " + e.getMessage());
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "AAListener - error " + e.getMessage());
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "AAListener - error " + e.getMessage());
                    }
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "AAListener - error " + e.getMessage());
                    }
                }
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "AAListener - error " + e.getMessage());
                    }
                }
            }
        }

        private void connectToPhone(BluetoothDevice device) {
            try {
                device.createRfcommSocketToServiceRecord(HSP_UUID).connect();
                innerConnectToPhone();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void innerConnectToPhone() {
            try {
                WifiInfoRequestMessage.WifiInfoRequest.Builder newBuilder =
                        WifiInfoRequestMessage.WifiInfoRequest.newBuilder();
                newBuilder.setIpAddress(Constants.IP_ADDRESS);
                newBuilder.setPort(Constants.TCP_PORT);
                sendToPhone(newBuilder.build().toByteArray(), (short) 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendToPhone(byte[] bArr, short s) throws IOException {
            ByteBuffer allocate = ByteBuffer.allocate(bArr.length + 4);
            allocate.put((byte) ((bArr.length >> 8) & 255));
            allocate.put((byte) (bArr.length & 255));
            allocate.putShort(s);
            allocate.put(bArr);
            outputStream.write(allocate.array());
            readFromPhone();
        }

        private void readFromPhone() throws IOException {
            byte[] bArr = new byte[1024];
            inputStream.read(bArr);
            short s = (short) (((bArr[2] & 255) << 8) | (bArr[3] & 255));
            if (s == 2) {
                WifiSecurityResponseMessage.WifiSecurityReponse.Builder newBuilder =
                        WifiSecurityResponseMessage.WifiSecurityReponse.newBuilder();
                newBuilder.setSsid(BluetoothAdapter.getDefaultAdapter().getName());
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                newBuilder.setBssid(wifiManager.getConnectionInfo().getMacAddress());
                newBuilder.setAccessPointType(
                        WifiSecurityResponseMessage.WifiSecurityReponse.AccessPointType.STATIC);
                newBuilder.setKey(Constants.WIFI_PASSWORD);
                newBuilder.setSecurityMode(
                        WifiSecurityResponseMessage.WifiSecurityReponse.SecurityMode.WPA2_PERSONAL);
                sendToPhone(newBuilder.build().toByteArray(), (short) 3);
            }
        }
    }
}
