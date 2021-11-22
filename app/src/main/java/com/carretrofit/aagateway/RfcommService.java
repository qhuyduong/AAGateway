package com.carretrofit.aagateway;

import android.app.Notification;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.carretrofit.aagateway.proto.Wifi.WifiConnectionStatus;
import com.carretrofit.aagateway.proto.Wifi.WifiInfoResponse;
import com.carretrofit.aagateway.proto.Wifi.WifiStartRequest;
import com.carretrofit.aagateway.proto.Wifi.WifiStartResponse;
import com.dseltec.widget.DsToast;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class RfcommService extends Service {
    private static final String TAG = "AAGateWayRfcommService";
    private static final String WAA_NAME = "Wireless Android Auto";
    private static final UUID WAA_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");
    private static final short WIFI_START_REQUEST = 1;
    private static final short WIFI_INFO_REQUEST = 2;
    private static final short WIFI_INFO_RESPONSE = 3;
    private static final short WIFI_START_RESPONSE = 7;
    private static final short WIFI_CONNECTION_STATUS = 6;

    private boolean running = false;

    private BroadcastReceiver receiver =
            new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                        int intExtra =
                                intent.getIntExtra(
                                        BluetoothAdapter.EXTRA_CONNECTION_STATE,
                                        BluetoothAdapter.STATE_DISCONNECTED);
                        if (intExtra == BluetoothAdapter.STATE_CONNECTED) {
                            BluetoothDevice device =
                                    (BluetoothDevice)
                                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            Log.d(TAG, "Device " + device + " connected");
                            new Thread(new WAAListener()).start();
                        }
                    }
                }
            };

    @Override
    public void onCreate() {
        Notification notification =
                new Notification.Builder(this).setContentTitle("Rfcomm Service").build();
        startForeground(2, notification);
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            return START_STICKY;
        }
        running = true;
        Log.d(TAG, "Service started");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        unregisterReceiver(receiver);
        stopForeground(true);
        Log.d(TAG, "Service destroyed");
    }

    private class WAAListener implements Runnable {
        private DataInputStream inputStream;
        private OutputStream outputStream;

        public void run() {
            BluetoothServerSocket serverSocket = null;
            BluetoothSocket socket = null;
            try {
                Log.d(TAG, "Listening to WAA");
                serverSocket =
                        BluetoothAdapter.getDefaultAdapter()
                                .listenUsingRfcommWithServiceRecord(WAA_NAME, WAA_UUID);
                socket = serverSocket.accept();
                serverSocket.close();
                Log.d(TAG, "WAA conected");

                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = socket.getOutputStream();

                sendWifiStartRequest();
                handleMessage();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (socket != null) {
                        socket.close();
                    }
                    if (serverSocket != null) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                }
            }
        }

        private void handleMessage() throws IOException {
            boolean done = false;
            while (!done) {
                byte[] bytes = new byte[1024];
                int length = inputStream.read(bytes);
                short type = (short) (((bytes[2] & 255) << 8) | (bytes[3] & 255));

                switch (type) {
                    case WIFI_INFO_REQUEST:
                        Log.d(TAG, "Received wifi info request");
                        sendWifiInfoResponse();
                        break;
                    case WIFI_START_RESPONSE:
                        new Handler(Looper.getMainLooper())
                                .post(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                DsToast toast =
                                                        DsToast.makeText(
                                                                getApplicationContext(),
                                                                getString(
                                                                        R.string.connecting_to_waa),
                                                                1);
                                                toast.show();
                                            }
                                        });

                        Log.d(
                                TAG,
                                "Received wifi start response "
                                        + WifiStartResponse.parseFrom(
                                                        Arrays.copyOfRange(bytes, 4, length))
                                                .toString());
                        break;
                    case WIFI_CONNECTION_STATUS:
                        Log.d(
                                TAG,
                                "Received wifi connection status "
                                        + WifiConnectionStatus.parseFrom(
                                                        Arrays.copyOfRange(bytes, 4, length))
                                                .toString());
                        done = true;
                        break;
                }
            }
        }

        private void sendWifiStartRequest() throws IOException {
            WifiStartRequest request =
                    WifiStartRequest.newBuilder()
                            .setIpAddress(Constants.IP_ADDRESS)
                            .setPort(Constants.TCP_PORT)
                            .build();
            Log.d(TAG, "Sending wifi start request " + request.toString());
            byte[] bytes = request.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
            buffer.put((byte) ((bytes.length >> 8) & 255));
            buffer.put((byte) (bytes.length & 255));
            buffer.putShort(WIFI_START_REQUEST);
            buffer.put(bytes);
            outputStream.write(buffer.array());
        }

        private void sendWifiInfoResponse() throws IOException {
            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            WifiInfoResponse response =
                    WifiInfoResponse.newBuilder()
                            .setSsid(BluetoothAdapter.getDefaultAdapter().getName())
                            .setPassword(Constants.WIFI_PASSWORD)
                            .setBssid(wifiManager.getConnectionInfo().getMacAddress())
                            .setSecurityMode(WifiInfoResponse.SecurityMode.WPA2_PERSONAL)
                            .build();
            Log.d(TAG, "Sending wifi info response " + response.toString());
            byte[] bytes = response.toByteArray();
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
            buffer.put((byte) ((bytes.length >> 8) & 255));
            buffer.put((byte) (bytes.length & 255));
            buffer.putShort(WIFI_INFO_RESPONSE);
            buffer.put(bytes);
            outputStream.write(buffer.array());
        }
    }
}
