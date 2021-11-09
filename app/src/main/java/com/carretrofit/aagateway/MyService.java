package com.carretrofit.aagateway;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class MyService extends Service {
    private static final String TAG = "AAGateWayService";
    private static final String USB_ACCESSORY = "/dev/usb_accessory";
    private static final String UDC = "/sys/kernel/config/usb_gadget/g1/UDC";
    private static final String DUMMY_UDC = "dummy_udc.0";
    private static final byte[] VERSION_REQUEST = {0, 3, 0, 6, 0, 1, 0, 1, 0, 2};
    private static final byte[] VERSION_RESPONSE = {0, 3, 0, 8, 0, 2, 0, 1, 0, 4, 0, 0};
    private static final int MAX_BUFFER_LENGTH = 16384;
    private static final String[] IP_NEIGH_COMMAND = {"ip", "neigh", "show", "dev", "wlan0"};

    private ParcelFileDescriptor usbFileDescriptor;
    private FileOutputStream usbOutputStream;
    private FileInputStream usbInputStream;
    private FileOutputStream udcOutputStream;

    private static OutputStream tcpOutputStream;
    private static DataInputStream tcpInputStream;

    private boolean running = false;
    private boolean tcpConnected = false;
    private boolean tcpCompleted = false;
    private boolean usbCompleted = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            Log.d(TAG, "Service already running");
            return START_STICKY;
        }

        Log.d(TAG, "Service started");
        File file = new File(USB_ACCESSORY);
        try {
            usbFileDescriptor =
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            FileDescriptor fd = usbFileDescriptor.getFileDescriptor();
            usbInputStream = new FileInputStream(fd);
            usbOutputStream = new FileOutputStream(fd);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot find usb accessory");
            stopSelf();
            return START_STICKY;
        }

        running = true;
        tcpConnected = false;
        tcpCompleted = false;
        usbCompleted = false;

        new Thread(new TcpThread()).start();
        new Thread(new UdpThread()).start();
        new Thread(new UsbThread()).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (udcOutputStream != null) {
            try {
                udcOutputStream.write("\n".getBytes());
            } catch (Exception e) {
                Log.e(TAG, "Cannot unbind accessory");
            }
        }
        Log.d(TAG, "Service destroyed");
    }

    class TcpThread implements Runnable {
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(5288, 5);
                serverSocket.setReuseAddress(true);

                Log.d(TAG, "tcp - listening");
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(5000);
                Log.d(TAG, "tcp - phone has connected back");

                tcpConnected = true;

                // Bind accessory to UDC
                udcOutputStream = new FileOutputStream(UDC);
                udcOutputStream.write(DUMMY_UDC.getBytes());

                tcpOutputStream = clientSocket.getOutputStream();
                tcpInputStream = new DataInputStream(clientSocket.getInputStream());
                tcpOutputStream.write(VERSION_REQUEST);
                tcpOutputStream.flush();
                byte[] buf = new byte[MAX_BUFFER_LENGTH];
                tcpInputStream.read(buf);

                tcpCompleted = true;

                // wait for usb initialization
                while (!usbCompleted && running) {
                    Log.d(TAG, "tcp - waiting for usb");
                    Thread.sleep(100);
                }

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
                    usbOutputStream.write(Arrays.copyOf(buf, messageLength + headerLength));
                }

                serverSocket.close();
                Log.d(TAG, "tcp - end");
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "tcp - error " + e.getMessage());
                stopSelf();
            }
        }
    }

    class UdpThread implements Runnable {
        public void run() {
            Log.d(TAG, "udp - start");
            byte[] buf = new byte[] {'S'};
            Process process;
            BufferedReader br;
            String line;
            DatagramPacket packet;
            InetAddress addr;

            while (!tcpConnected) {
                try {
                    process = Runtime.getRuntime().exec(IP_NEIGH_COMMAND);
                    br = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    while ((line = br.readLine()) != null) {
                        Log.d(TAG, "tcp - ip neigh output " + line);
                        String[] splitted = line.split(" +");
                        if ((splitted == null) || (splitted.length < 1)) {
                            Log.d(TAG, "tcp - not splitted?!");
                            continue;
                        }

                        addr = InetAddress.getByName(splitted[0]);
                        Log.d(TAG, "udp - sending trigger to " + splitted[0]);
                        // send to every address, only the phone with AAStarter will try to connect
                        // back
                        packet = new DatagramPacket(buf, buf.length, addr, 4455);

                        DatagramSocket socket = new DatagramSocket();
                        socket.send(packet);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "udp - error " + e.getMessage());
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.e(TAG, "udp - error " + e.getMessage());
                }
            }

            Log.d(TAG, "udp - end");
        }
    }

    class UsbThread implements Runnable {
        public void run() {
            try {
                Log.d(TAG, "usb - start");
                byte buf[] = new byte[MAX_BUFFER_LENGTH];
                usbInputStream.read(buf);
                usbOutputStream.write(VERSION_RESPONSE);
                usbCompleted = true;

                while (!tcpCompleted && running) {
                    Log.d(TAG, "usb - waiting for local");
                    Thread.sleep(100);
                }

                int length;
                while (running) {
                    length = usbInputStream.read(buf);
                    tcpOutputStream.write(Arrays.copyOf(buf, length));
                }

                usbFileDescriptor.close();
                Log.d(TAG, "usb - end");
                stopSelf();
            } catch (Exception e) {
                Log.e(TAG, "usb - error " + e.getMessage());
                stopSelf();
            }
        }
    }
}
