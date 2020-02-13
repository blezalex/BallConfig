package ride.ballconfig;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import proto.Protocol;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SerialComm.ProtoHandler {
    public final int GET_SETTINGS_CODE = 10;
    public final int READ_FILE_REQUEST_CODE = 11;
    public final int WRITE_FILE_REQUEST_CODE = 12;

    public final int PICK_DEVICE_CODE = 20;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GET_SETTINGS_CODE && resultCode == RESULT_OK) {
            byte[] config = data.getByteArrayExtra("config");
            try {
                cfg.mergeFrom(config);
        //        communicator.sendConfig(cfg.build());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            } catch (IOException e) {
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
                showToast( e.toString(), Toast.LENGTH_LONG);
            } catch (IOException e) {
                showToast( e.toString(), Toast.LENGTH_LONG);
            }
            showToast("Loaded", Toast.LENGTH_SHORT);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    Handler mHandler;

    static final int MSG_GOT_CONFIG = 1;
    static final int MSG_GOT_STATS = 2;
    static final int MSG_GENERIC = 3;
    static final int MSG_GOT_DEBUG = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cfg = Protocol.Config.newBuilder();
        SettingsActivity.setDefaults(cfg);

        communicator = new BluetoothWorker(this, new SerialComm.ProtoHandler() {
            @Override
            public void OnGeneric(Protocol.ReplyId reply) {
                Message message = mHandler.obtainMessage(MSG_GENERIC, reply);
                message.sendToTarget();
            }

            @Override
            public void OnConfig(Protocol.Config cfg) {
                Message message = mHandler.obtainMessage(MSG_GOT_CONFIG, cfg);
                message.sendToTarget();
            }

            @Override
            public void OnStats(Protocol.Stats stats) {
                Message message = mHandler.obtainMessage(MSG_GOT_STATS, stats);
                message.sendToTarget();
            }

            @Override
            public void OnDebug(byte[] data) {
                Message message = mHandler.obtainMessage(MSG_GOT_DEBUG, data);
                message.sendToTarget();
            }
        });

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {

                switch (inputMessage.what) {
                    case MSG_GOT_CONFIG:
                        OnConfig((Protocol.Config)inputMessage.obj);
                        break;

                    case MSG_GOT_STATS:
                        OnStats((Protocol.Stats)inputMessage.obj);
                        break;

                    case MSG_GOT_DEBUG:
                        OnDebug((byte[])inputMessage.obj);
                        break;

                    case MSG_GENERIC:
                        OnGeneric((Protocol.ReplyId)inputMessage.obj);
                        break;

                    default:
                        super.handleMessage(inputMessage);
                }

            }
        };

        GraphView graph = (GraphView) findViewById(R.id.graph);
        series_ = new LineGraphSeries<>(new DataPoint[] {});
        graph.addSeries(series_);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(max_point);

        // activate horizontal zooming and scrolling
        graph.getViewport().setScalable(true);

        // activate horizontal scrolling
        graph.getViewport().setScrollable(true);

        // activate horizontal and vertical zooming and scrolling
        graph.getViewport().setScalableY(true);

        // activate vertical scrolling
        graph.getViewport().setScrollableY(true);

        mTimer1 = new Runnable() {
            @Override
            public void run() {
                try {
                    communicator.sendMsg(Protocol.RequestId.GET_DEBUG_BUFFER);
                } catch (IOException e) {
                    showError(e.toString());
                }
                mHandler.postDelayed(this, 500);
            }
        };
    }

    Protocol.Config.Builder cfg;

    BluetoothWorker communicator;

    boolean connected = false;

    LineGraphSeries<DataPoint> series_;

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        String address = sharedPref.getString("device_address", null);

        if (address == null) {
            startActivityForResult(
                    new Intent(this, ListBluetooth.class), PICK_DEVICE_CODE);
            return;
        }

        if (!connected) {
            try {
                communicator.connectToDevice(address);
                communicator.sendMsg(Protocol.RequestId.READ_CONFIG);
                connected = true;
            } catch (IOException e) {
                showError("failed to get stream: " + e.getMessage() + ".");
            }
        }

        if (connected) {
            mHandler.postDelayed(mTimer1, 500);
        }
    }


    @Override
    public void OnGeneric(Protocol.ReplyId reply) {
        showToast(reply.toString(), Toast.LENGTH_SHORT);
    }

    @Override
    public void OnConfig(Protocol.Config deviceConfig) {
        showToast("Got config", Toast.LENGTH_SHORT);
        cfg.clear().mergeFrom(deviceConfig);
    }

    @Override
    public void OnStats(Protocol.Stats stats) {
        showToast(stats.toString(), Toast.LENGTH_LONG);
    }

    int last_point_data_ = 0;

    static final int max_point = 250;

    private Runnable mTimer1;

    private Toast lastToast = null;

    private void showToast(String text, int duration) {
        if (lastToast != null) {
            lastToast.cancel();
        }
        lastToast =
                Toast.makeText(getApplicationContext(),text, duration);
        lastToast.show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mTimer1);
    }

    @Override
    public void OnDebug(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            series_.appendData(new DataPoint(last_point_data_++ , data[i]), false, max_point, true);
        }

        series_.appendData(new DataPoint(last_point_data_++ , data[data.length - 1]), true, max_point, false);
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
                    communicator.sendMsg(Protocol.RequestId.READ_CONFIG);
                break;

            case R.id.callibrateAcc:
                communicator.sendMsg(Protocol.RequestId.CALLIBRATE_ACC);
                break;

            case R.id.saveToFlash:
                communicator.sendMsg(Protocol.RequestId.SAVE_CONFIG);
                break;

            case R.id.getStats:
                communicator.sendMsg(Protocol.RequestId.GET_STATS);
                break;

            case R.id.saveToBoard:
                communicator.sendConfig(cfg.build());
                break;

        }
        } catch (IOException e) {
            showError(e.toString());
        }
    }
}
