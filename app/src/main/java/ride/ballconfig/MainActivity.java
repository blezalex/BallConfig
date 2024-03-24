package ride.ballconfig;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import proto.Protocol;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public final int GET_SETTINGS_CODE = 10;
    public final int READ_FILE_REQUEST_CODE = 11;
    public final int WRITE_FILE_REQUEST_CODE = 12;

    public final int PICK_DEVICE_CODE = 20;

    private boolean have_config = false;
    Descriptors.Descriptor configDescriptor;
    Descriptors.Descriptor statsDescriptor;
    byte[] compressedConfigDescriptor;
    DynamicMessage.Builder cfg;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (cfg == null) {
            showError("No descriptor");
            return;
        }

        if (requestCode == GET_SETTINGS_CODE && resultCode == RESULT_OK) {
            byte[] config = data.getByteArrayExtra("config");
            try {
                showToast(Integer.toString(config.length), Toast.LENGTH_SHORT);
                cfg.clear().mergeFrom(config);
                //        communicator.sendConfig(cfg.build());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            return;
        }

        if (requestCode == WRITE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                OutputStream out = getContentResolver().openOutputStream(data.getData());
                OutputStreamWriter writer = new OutputStreamWriter(out);
                writer.write(cfg.toString());
                writer.close();
                out.close();
            } catch (FileNotFoundException e) {
                showToast(e.toString(), Toast.LENGTH_LONG);
            } catch (IOException e) {
                showToast(e.toString(), Toast.LENGTH_LONG);
            }
            showToast("Saved", Toast.LENGTH_SHORT);
        }

        if (requestCode == READ_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                InputStreamReader reader = new InputStreamReader(in);
                DescriptorUtils.setFieldsToTheirDefaultValues(cfg);
                TextFormat.getParser().merge(reader, cfg);
                reader.close();
                in.close();
            } catch (FileNotFoundException e) {
                showToast(e.toString(), Toast.LENGTH_LONG);
            } catch (IOException e) {
                showToast(e.toString(), Toast.LENGTH_LONG);
            }
            showToast("Loaded", Toast.LENGTH_SHORT);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private Handler timerHandler;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    switch (intent.getAction()) {
                        case BtService.Constants.MSG_CONFIG:
                            OnConfig(intent.getByteArrayExtra(BtService.Constants.DATA));
                            return;

                        case BtService.Constants.MSG_STATS:
                            OnStats(Protocol.Stats.parseFrom(intent.getByteArrayExtra(BtService.Constants.DATA)));
                            return;

                        case BtService.Constants.MSG_GENERIC:
                            OnGeneric(Protocol.ReplyId.forNumber(intent.getIntExtra(BtService.Constants.DATA, 0)));
                            return;

                        case BtService.Constants.MSG_CONFIG_DESCRIPTOR:
                            OnConfigDescriptor(intent.getByteArrayExtra(BtService.Constants.DATA));
                            return;

                        case BtService.Constants.MSG_CONNECTION_STATE_CHANGE:
                            OnConnectionStateChanged(SerialComm.ConnectionState.values()[(intent.getIntExtra(BtService.Constants.DATA, 0))]);

                        default:
                            break;
                    }
                } catch (InvalidProtocolBufferException e) {
                    showError(e.toString());
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(
                        BtService.Constants.MSG_CONFIG));

        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(
                        BtService.Constants.MSG_STATS));

        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(
                        BtService.Constants.MSG_GENERIC));

        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(
                        BtService.Constants.MSG_CONFIG_DESCRIPTOR));

        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(
                        BtService.Constants.MSG_CONNECTION_STATE_CHANGE));

        Intent intent = new Intent(this, BtService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        timerHandler = new Handler();
        mTimer1 = new Runnable() {
            int val = 0;

            @Override
            public void run() {
                try {
                    if (btService != null) {
                        if (!have_config) {
                            if ((val++ % 4 == 0)) {
                                btService.sendMsg(configDescriptor == null ?  Protocol.RequestId.GET_CONFIG_DESCRIPTOR : Protocol.RequestId.READ_CONFIG);
                            }

                        }
                        else {
                            btService.sendMsg(Protocol.RequestId.GET_STATS);
                        }
                    }
                } catch (IOException e) {
                    showError(e.toString());
                }
                timerHandler.postDelayed(mTimer1, 250);
            }
        };
    }

    boolean connected = false;

    BtService btService;

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Because we have bound to an explicit
            // service that is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            BtService.LocalBinder binder = (BtService.LocalBinder) service;
            btService = binder.getService();

            if (!connected) {
                try {
                    String address = getAddress();
                    btService.connectToDevice(address);
                    btService.sendMsg(Protocol.RequestId.GET_CONFIG_DESCRIPTOR);
                    //btService.sendMsg(Protocol.RequestId.READ_CONFIG);
                    connected = true;
                } catch (IOException e) {
                    showError("failed to get stream: " + e.getMessage() + ".");
                }
            }
            startTimer();
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            btService = null;
        }
    };

    boolean timerRunning = false;

    void startTimer() {
        if (timerRunning)
            return;

        timerHandler.postDelayed(mTimer1, 500);
        timerRunning = true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        String address = getAddress();
        if (address == null) {
            startActivityForResult(
                    new Intent(this, ListBluetooth.class), PICK_DEVICE_CODE);
            return;
        }

        if (connected) {
            startTimer();
        }
    }

    private String getAddress() {
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        return sharedPref.getString("device_address", null);
    }


    private void OnGeneric(Protocol.ReplyId reply) {
        showToast(reply.toString(), Toast.LENGTH_SHORT);
    }

    private void OnConfig(byte[] deviceConfig) {
        if (configDescriptor == null) {
            showToast("Error, got config before descriptor", Toast.LENGTH_SHORT);
            return;
        }

        if (cfg == null) {
            cfg = DynamicMessage.newBuilder(configDescriptor);
        }

        showToast("Got config", Toast.LENGTH_SHORT);
        have_config = true;

        try {
            cfg.clear().mergeFrom(deviceConfig);
        }
        catch (Exception ex) {
            showToast("Bad config: " + ex.toString(), Toast.LENGTH_LONG);
        }
    }

    private void OnConfigDescriptor(byte[] compressedDescriptor) {
        showToast("Got descriptor. Len: " + compressedDescriptor.length, Toast.LENGTH_SHORT);

        if (configDescriptor != null) {
            showToast("Already have config descriptor." + compressedDescriptor.length, Toast.LENGTH_SHORT);
            return;
        }

        try {
            configDescriptor = DescriptorUtils.parseConfigDescriptor(compressedDescriptor);
            this.compressedConfigDescriptor = compressedDescriptor;
            cfg = DynamicMessage.newBuilder(configDescriptor);
        }
        catch (Exception ex) {
            showToast("Failed to parse config descriptor: " + ex.toString(), Toast.LENGTH_LONG);
        }
    }

    private void OnConnectionStateChanged(SerialComm.ConnectionState new_state) {
        TextView statsControl = findViewById(R.id.stats);
        statsControl.setText(new_state.toString());
    }


    float getErpmToDistConst() {
        if (configDescriptor == null || cfg == null)
        {
            return 0;
        }

        Descriptors.FieldDescriptor misc_field = configDescriptor.findFieldByName("misc");
        if (misc_field == null) {
            return 0;
        }

        DynamicMessage miscMsg = (DynamicMessage) cfg.getField(misc_field);
        Descriptors.FieldDescriptor erpm_filed = miscMsg.getDescriptorForType().findFieldByName("erpm_to_dist_const");
        if (erpm_filed == null) {
            return 0;
        }
        return (float) miscMsg.getField(erpm_filed);
    }

    private void OnStats(Protocol.Stats stats) {
        TextView statsControl = findViewById(R.id.stats);
        statsControl.setText(stats.toString());

        NumberFormat formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(2);
        formatter.setGroupingUsed(false);

        TextView battV = findViewById(R.id.tvBatteryV);
        battV.setText("Battery: " +
                formatter.format(stats.getBattVoltage()) + "V");

        TextView speed = findViewById(R.id.tvSpeed);



        float speed_m_sec = Math.abs(stats.getSpeed()) * getErpmToDistConst() / 60;
        speed.setText("Speed: " + formatter.format( speed_m_sec * 3.6) + " km/h");

        TextView distance = findViewById(R.id.tvDist);
        distance.setText("Distance traveled: " +
                formatter.format(stats.getDistanceTraveled() * getErpmToDistConst() /  3 / 2 / 1000) + " km");

    }

    private Runnable mTimer1;

    private Toast lastToast = null;

    private void showToast(String text, int duration) {
        if (lastToast != null) {
            lastToast.cancel();
        }
        lastToast =
                Toast.makeText(getApplicationContext(), text, duration);
        lastToast.show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        timerHandler.removeCallbacks(mTimer1);
        timerRunning = false;
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
    }

    void showError(String text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    @Override
    public void onClick(View v) {
        try {
            switch (v.getId()) {
                case R.id.changeSettings:
                    startActivityForResult(
                            new Intent(this, SettingsActivity.class).putExtra("config", cfg.build().toByteArray()).putExtra("configDescriptor", compressedConfigDescriptor) , GET_SETTINGS_CODE);
                    break;

                case R.id.readFromFile:
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("text/*");
                    startActivityForResult(i, READ_FILE_REQUEST_CODE);
                    break;

                case R.id.saveToFile:
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                    String currentDateandTime = sdf.format(new Date());
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .setType("text/*")
                            .putExtra(Intent.EXTRA_TITLE, "boardConfig_" + currentDateandTime)
                            .addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, WRITE_FILE_REQUEST_CODE);
                    break;

                case R.id.readFromBoard:
                    btService.sendMsg(Protocol.RequestId.READ_CONFIG);
                    break;

                case R.id.plot:
                    startActivity(new Intent(this, PlotActivity.class));
                    break;

                case R.id.saveToFlash:
                    btService.sendMsg(Protocol.RequestId.SAVE_CONFIG);
                    break;

                case R.id.saveToBoard:
                    btService.sendConfig(cfg.build().toByteArray());
                    break;

                case R.id.passthough:
                    btService.sendMsg(Protocol.RequestId.TOGGLE_PASSTHROUGH);
                    break;
            }
        } catch (IOException e) {
            showError(e.toString());
        }
    }
}
