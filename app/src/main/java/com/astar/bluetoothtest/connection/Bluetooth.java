package com.astar.bluetoothtest.connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * @Author Astar
 */

public class Bluetooth {

    private static final boolean D = true;
    private static final String TAG = "BluetoothService";

    private static final String APP_NAME = "com.astar.bluetoothtest";
    private static final UUID BASE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //private static final UUID BASE_UUID = UUID.fromString("3ee496e8-b3cd-11e9-a2a3-2a2ae2dbcce4");

    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_LISTEN = 3;
    public static final int MESSAGE_READ = 4;
    public static final int MESSAGE_WRITE = 5;
    public static final int MESSAGE_STATE_CHANGED = 6;

    private BluetoothAdapter mAdapter;
    private Context mContext;
    private Handler mHandler;
    private int mState;
    private static Bluetooth sInstance;

    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private Bluetooth(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            Log.e(TAG, "Bluetooth not support", null);
        }
        mAdapter.enable();
    }

    public static Bluetooth getInstance(Context context, Handler handler) {
        if (D) Log.d(TAG, "getInstance()");

        if (sInstance == null) {
            sInstance = new Bluetooth(context, handler);
        }
        return sInstance;
    }

    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState()");
        mState = state;
        mHandler.obtainMessage(MESSAGE_STATE_CHANGED, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return mState;
    }


    public void enable() {
        mAdapter.enable();
    }

    public void disable() {
        mAdapter.disable();
    }

    public boolean isEnabled() {
        return mAdapter.isEnabled();
    }

    private class AcceptThread extends Thread {

        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            if (D) Log.d(TAG, "AcceptThread: constructor");
            BluetoothServerSocket tmp = null;
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, BASE_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Error listening", e);
            }
            mmServerSocket = tmp;
        }

        @Override
        public void run() {
            if (D) Log.d(TAG, "run: AcceptThread BEGIN");
            BluetoothSocket socket = null;

            while (mState != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }
                if (socket != null) {
                    synchronized (Bluetooth.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Error close socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.d(TAG, "run: AcceptThread END");
        }

        public void cancel() {
            if (D) Log.d(TAG, "Cancel AcceptThread");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() error server socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {

        private final BluetoothDevice mmDevice;
        private final BluetoothSocket mmSocket;


        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(BASE_UUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: Error created socket", e);
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            mAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    Log.e(TAG, "run: unable to close() socket", e);
                }
                Bluetooth.this.start();
                return;
            }
            synchronized (Bluetooth.this) {
                mConnectedThread = null;
            }

            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInputStream;
        private final OutputStream mmOutputStream;

        public ConnectedThread(BluetoothSocket socket) {
            if (D) Log.d(TAG, "create ConnectedThread()");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: temp sockets not created", e);
            }

            mmInputStream = tmpIn;
            mmOutputStream = tmpOut;
        }

        @Override
        public void run() {
            if (D) Log.d(TAG, "run: BEGIN mConnectedThread");

            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInputStream.read(buffer);
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "run: disconnect", e);
                    connectionFailed();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutputStream.write(buffer);
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "write: Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() of connect socket failed", e);
            }
        }
    }

    public void start() {
        if (D) Log.d(TAG, "start()");

        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }

        setState(STATE_LISTEN);
    }

    public synchronized void stop() {
        if (D) { Log.d(TAG, "stop()"); }
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread!= null) { mConnectedThread.cancel(); mConnectedThread= null; }
        if (mAcceptThread != null) { mAcceptThread.cancel(); mAcceptThread = null; }
        setState(STATE_NONE);
    }

    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        }

        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();

        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected()");

        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }
        if (mAcceptThread != null) { mAcceptThread.cancel(); mAcceptThread = null; }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();


        setState(STATE_CONNECTED);
    }

    private synchronized void connectionFailed() {
        setState(STATE_LISTEN);
        Log.e(TAG, "connectionFailed: ");
    }

    public synchronized void write(byte[] buffer) {

            mConnectedThread.write(buffer);

    }
}
