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
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GET_SETTINGS_CODE && resultCode == RESULT_OK) {
            byte[] config = data.getByteArrayExtra("config");
            try {
                cfg.mergeFrom(config);
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
                //  Protocol.Config.Builder builder =  Protocol.Config.newBuilder();
                InputStreamReader reader = new InputStreamReader(in);
                cfg.clear();
                SettingsActivity.setDefaults(cfg);
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

        cfg = Protocol.Config.newBuilder();
        SettingsActivity.setDefaults(cfg);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    switch (intent.getAction()) {
                        case BtService.Constants.MSG_CONFIG:
                            OnConfig(Protocol.Config.parseFrom(intent.getByteArrayExtra(BtService.Constants.DATA)));
                            return;

                        case BtService.Constants.MSG_STATS:
                            OnStats(Protocol.Stats.parseFrom(intent.getByteArrayExtra(BtService.Constants.DATA)));
                            return;

                        case BtService.Constants.MSG_GENERIC:
                            OnGeneric(Protocol.ReplyId.forNumber(intent.getIntExtra(BtService.Constants.DATA, 0)));
                            return;
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
                            if (val++ % 2 > 0)
                               btService.sendMsg(Protocol.RequestId.READ_CONFIG);
                        }
                        else {
                            btService.sendMsg(Protocol.RequestId.GET_STATS);
                        }
                    }
                } catch (IOException e) {
                    showError(e.toString());
                }
                timerHandler.postDelayed(mTimer1, 500);
            }
        };
    }

    Protocol.Config.Builder cfg;

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
                    btService.sendMsg(Protocol.RequestId.READ_CONFIG);
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

    private void OnConfig(Protocol.Config deviceConfig) {
        showToast("Got config", Toast.LENGTH_SHORT);
        have_config = true;
        cfg.clear().mergeFrom(deviceConfig);
    }

    private void OnStats(Protocol.Stats stats) {
        TextView statsControl = findViewById(R.id.stats);
        statsControl.setText(stats.toString());

        NumberFormat formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(2);

        TextView battV = findViewById(R.id.tvBatteryV);
        battV.setText("Battery: " +
                formatter.format(stats.getBattVoltage()) + "V");

        TextView speed = findViewById(R.id.tvSpeed);

        float speed_m_sec = Math.abs(stats.getSpeed()) * cfg.getMisc().getErpmToDistConst() / 60;
        speed.setText("Speed: " + formatter.format( speed_m_sec * 3.6) + " km/h");

        TextView distance = findViewById(R.id.tvDist);
        distance.setText("Distance traveled: " +
                formatter.format(stats.getDistanceTraveled() * cfg.getMisc().getErpmToDistConst() /  3 / 2 / 1000) + " km");

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
                            new Intent(this, SettingsActivity.class).putExtra("config", cfg.build().toByteArray()), GET_SETTINGS_CODE);
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
                    btService.sendConfig(cfg.build());
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
