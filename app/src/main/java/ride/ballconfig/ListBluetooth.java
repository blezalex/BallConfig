package ride.ballconfig;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

import proto.Protocol;

public class ListBluetooth extends AppCompatActivity {
    BluetoothWorker communicator;

    AppCompatActivity that;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_bluetooth);

        that = this;

        communicator = new BluetoothWorker(this, new SerialComm.ProtoHandler() {
            @Override
            public void OnGeneric(Protocol.ReplyId reply) {
            }

            @Override
            public void OnConfig(Protocol.Config cfg) {
            }

            @Override
            public void OnStats(Protocol.Stats stats) {
            }

            @Override
            public void OnDebug(byte[] data) {

            }
        });

        communicator.setupBluetooth();

        Set<BluetoothDevice> devices = communicator.mBluetoothAdapter.getBondedDevices();
        BluetoothDevice[] devices_array = devices.toArray(new BluetoothDevice[0]);

        UsersAdapter adapter = new UsersAdapter(this, devices_array);

        ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(adapter);
    }

    public class UsersAdapter extends ArrayAdapter<BluetoothDevice> {

        public UsersAdapter(Context context, BluetoothDevice[] devices) {
            super(context, R.layout.device_list_item, devices);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            BluetoothDevice device = getItem(position);
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.device_list_item, parent, false);
            }

            TextView text = convertView.findViewById(R.id.label);
            text.setText(device.getName() + " (" + device.getAddress() + ")");
            text.setTag(device.getAddress());

            text.setOnClickListener(view -> {
                String address = (String) view.getTag();

                SharedPreferences sharedPref = view.getContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("device_address", address);
                editor.commit();

                Toast.makeText(getApplicationContext(), "Connecting", Toast.LENGTH_LONG).show();
                startActivity(new Intent(that, MainActivity.class));
                finish();
            });

            // Return the completed view to render on screen
            return convertView;
        }

    }
}
