package ride.ballconfig;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

public class ListBluetooth extends AppCompatActivity {
    BtService btService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_bluetooth);

        Intent intent = new Intent(this, BtService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Because we have bound to an explicit
            // service that is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            BtService.LocalBinder binder = (BtService.LocalBinder) service;
            onConnectedToService(binder.getService());
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            btService = null;
        }
    };

    private void onConnectedToService(BtService service) {
        btService = service;
        btService.setupBluetooth(ListBluetooth.this);
        Set<BluetoothDevice> devices = btService.getBondedDevices();
        BluetoothDevice[] devices_array = devices.toArray(new BluetoothDevice[0]);

        UsersAdapter adapter = new UsersAdapter(ListBluetooth.this, devices_array);

        ListView listView = findViewById(R.id.list);
        listView.setAdapter(adapter);
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
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
                startActivity(new Intent(ListBluetooth.this, MainActivity.class));
                finish();
            });

            // Return the completed view to render on screen
            return convertView;
        }

    }
}
