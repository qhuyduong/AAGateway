package com.carretrofit.aagateway;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.dseltec.widget.DsToast;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class RelayService extends Service {
    private static final String TAG = "AAGateWayRelayService";
    private static final String USB_ACCESSORY = "/dev/usb_accessory";
    private static final int MAX_BUFFER_LENGTH = 16384;

    private FileOutputStream usbOutputStream;
    private FileInputStream usbInputStream;

    private static OutputStream tcpOutputStream;
    private static DataInputStream tcpInputStream;

    private boolean running = false;
    private boolean tcpCompleted = false;
    private boolean usbCompleted = false;

    private TcpThread tcpThread;

    @Override
    public void onCreate() {
        Notification notification =
                new Notification.Builder(this).setContentTitle("Relay Service").build();
        startForeground(1, notification);
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

        tcpThread = new TcpThread();
        tcpThread.start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        tcpThread.terminate();
        stopForeground(true);
        Log.d(TAG, "Service destroyed");
    }

    class TcpThread extends Thread {
        private ServerSocket serverSocket = null;

        public void terminate() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                }
            }
        }

        public void run() {
            while (running) {
                Socket socket = null;
                try {
                    Log.d(TAG, "tcp - start");
                    serverSocket = new ServerSocket(Constants.TCP_PORT, 5);
                    serverSocket.setReuseAddress(true);
                    socket = serverSocket.accept();
                    serverSocket.close();

                    Log.d(TAG, "tcp - phone connected");

                    socket.setSoTimeout(5000);

                    UdcConnector.connect();

                    new UsbThread().start();

                    tcpOutputStream = socket.getOutputStream();
                    tcpInputStream = new DataInputStream(socket.getInputStream());

                    byte[] buf = new byte[MAX_BUFFER_LENGTH];
                    int messageLength;
                    int headerLength;

                    while (running) {
                        headerLength = 4;
                        tcpInputStream.readFully(buf, 0, 4);
                        messageLength = (buf[2] & 0xFF) << 8 | (buf[3] & 0xFF);

                        // Flag 9 means the header is 8 bytes long (read it in a separate byte
                        // array)
                        if ((int) buf[1] == 9) {
                            headerLength += 4;
                            tcpInputStream.readFully(buf, 4, 4);
                        }

                        tcpInputStream.readFully(buf, headerLength, messageLength);
                        usbOutputStream.write(buf, 0, messageLength + headerLength);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (tcpInputStream != null) {
                            tcpInputStream.close();
                        }
                        if (tcpOutputStream != null) {
                            tcpOutputStream.close();
                        }
                        if (socket != null) {
                            socket.close();
                        }
                        if (serverSocket != null) {
                            serverSocket.close();
                        }
                    } catch (IOException e) {
                    }

                    new Handler(Looper.getMainLooper())
                            .post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            DsToast toast =
                                                    DsToast.makeText(
                                                            getApplicationContext(),
                                                            getString(R.string.waa_disconnected),
                                                            1);
                                            toast.show();
                                        }
                                    });

                    UdcConnector.disconnect();

                    Log.d(TAG, "tcp - end");
                }
            }
        }
    }

    class UsbThread extends Thread {
        public void run() {
            ParcelFileDescriptor usbFileDescriptor = null;
            try {
                Log.d(TAG, "usb - start");
                usbFileDescriptor =
                        ParcelFileDescriptor.open(
                                new File(USB_ACCESSORY), ParcelFileDescriptor.MODE_READ_WRITE);
                usbInputStream = new FileInputStream(usbFileDescriptor.getFileDescriptor());
                usbOutputStream = new FileOutputStream(usbFileDescriptor.getFileDescriptor());

                byte buf[] = new byte[MAX_BUFFER_LENGTH];
                int length;

                while (running) {
                    length = usbInputStream.read(buf);
                    tcpOutputStream.write(buf, 0, length);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (usbInputStream != null) {
                        usbInputStream.close();
                    }
                    if (usbOutputStream != null) {
                        usbOutputStream.close();
                    }
                    if (usbFileDescriptor != null) {
                        usbFileDescriptor.close();
                    }
                } catch (IOException e) {
                }

                Log.d(TAG, "usb - end");
            }
        }
    }
}
