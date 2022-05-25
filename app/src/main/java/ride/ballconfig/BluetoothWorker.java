package ride.ballconfig;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import proto.Protocol;

public class BluetoothWorker {
    public BluetoothAdapter mBluetoothAdapter;
    BluetoothDevice mSerialDevice;
    BluetoothSocket btSocket;
    OutputStream outputStream;

    static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Thread btRecieveThrread;
    private BtService host;
    SerialComm comm;

    public BluetoothWorker(BtService host, SerialComm.ProtoHandler protoHandler) {
        this.host = host;
        comm = new SerialComm(protoHandler);
    }

    public void Stop() {
        if (btSocket != null) {
            try {
                btSocket.close();
                btSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean sendMsg(Protocol.RequestId id) throws IOException {
        if (outputStream == null) {
            return false;

        }
        SerialComm.sendMsg(outputStream, id);
        return true;
    }

    public boolean sendConfig(byte[] cfg) throws IOException {
        if (outputStream == null) {
            return false;
        }

        SerialComm.sendConfig(outputStream, cfg);
        return true;
    }

    public boolean setDebugStreamId(int id) throws IOException {
        if (outputStream == null) {
            return false;
        }

        SerialComm.setDebugStreamId(outputStream, id);
        return true;
    }


    public void setupBluetooth(Activity activity) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            showError("No Bluetooth!");
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            if (activity != null) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, 1);
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            else {
                showError("Bluetooth disabled. Please enable bluetooth.");
            }
        }
    }

    void showError(String text) {
        Toast.makeText(host.getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    public void connectToDevice(String address) throws IOException {
        setupBluetooth(null);
        mSerialDevice = mBluetoothAdapter.getRemoteDevice(address); // TODO: congifure bt device for 115200!
        if (mSerialDevice == null) {
            showError( "No paired device");
            return;
        }
        btSocket = mSerialDevice.createRfcommSocketToServiceRecord(SPP_UUID);
        btSocket.connect();

        outputStream = btSocket.getOutputStream();
        btRecieveThrread = new Thread(() -> {
            while (true) {
                try {
                    comm.RunReader(btSocket.getInputStream());
                } catch (IOException e) {
                    showError(e.toString()); // TODO: dispatch error via handler (cross thread)
                }
            }
        });
        btRecieveThrread.start();
    }
}
