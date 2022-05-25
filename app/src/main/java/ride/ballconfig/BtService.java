package ride.ballconfig;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;

import com.google.protobuf.DynamicMessage;

import java.io.IOException;
import java.util.Set;

import proto.Protocol;


public class BtService extends Service {
    public final class Constants {
        public static final String MSG_GENERIC = "ride.ballconfig.msg_generic";
        public static final String MSG_CONFIG = "ride.ballconfig.msg_config";
        public static final String MSG_STATS = "ride.ballconfig.msg_stats";
        public static final String MSG_DEBUG_INFO = "ride.ballconfig.msg_debug_info";
        public static final String MSG_CONFIG_DESCRIPTOR = "ride.ballconfig.msg_config_descriptor";

        public static final String DATA = "ride.ballconfig.msg_content_data";
    }


    private final IBinder binder = new LocalBinder();
    BluetoothWorker communicator;

    public class LocalBinder extends Binder {
        BtService getService() {
            return BtService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setupBluetooth(Activity activity) {
        communicator.setupBluetooth(activity);
    }

    public void connectToDevice(String address) throws IOException {
        communicator.connectToDevice(address);
    }

    public Set<BluetoothDevice> getBondedDevices() {
        return communicator.mBluetoothAdapter.getBondedDevices();
    }

    public void sendMsg(Protocol.RequestId id) throws IOException {
        communicator.sendMsg(id);
    }

    public void setDebugStreamId(int id) throws IOException {
        communicator.setDebugStreamId(id);
    }

    public void sendConfig(byte[] cfg) throws IOException {
        communicator.sendConfig(cfg);
    }

    public void onCreate() {
        communicator = new BluetoothWorker(this, new SerialComm.ProtoHandler() {
            @Override
            public void OnGeneric(Protocol.ReplyId reply) {
                Intent localIntent =
                        new Intent(Constants.MSG_GENERIC)
                                .putExtra(Constants.DATA, reply.getNumber());
                LocalBroadcastManager.getInstance(BtService.this).sendBroadcast(localIntent);
            }

            @Override
            public void OnConfig(byte[] cfg) {
                Intent localIntent =
                        new Intent(Constants.MSG_CONFIG)
                                .putExtra(Constants.DATA, cfg);
                LocalBroadcastManager.getInstance(BtService.this).sendBroadcast(localIntent);
            }

            @Override
            public void OnStats(Protocol.Stats stats) {
                Intent localIntent =
                        new Intent(Constants.MSG_STATS)
                                .putExtra(Constants.DATA, stats.toByteArray());
                LocalBroadcastManager.getInstance(BtService.this).sendBroadcast(localIntent);
            }

            @Override
            public void OnDebug(byte[] data) {
                Intent localIntent =
                        new Intent(Constants.MSG_DEBUG_INFO)
                                .putExtra(Constants.DATA, data);
                LocalBroadcastManager.getInstance(BtService.this).sendBroadcast(localIntent);
            }

            @Override
            public void OnConfigDescriptor(byte[] data) {
                Intent localIntent =
                        new Intent(Constants.MSG_CONFIG_DESCRIPTOR)
                                .putExtra(Constants.DATA, data);
                LocalBroadcastManager.getInstance(BtService.this).sendBroadcast(localIntent);
            }
        });
    }

    public void onDestroy() {
        communicator.Stop();
        communicator = null;
    }
}
