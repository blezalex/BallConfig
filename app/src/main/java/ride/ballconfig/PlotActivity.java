package ride.ballconfig;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;

import proto.Protocol;

public class PlotActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    BtService btService;
    BroadcastReceiver receiver;

    private Toast lastToast = null;

    private void showToast(String text, int duration) {
        if (lastToast != null) {
            lastToast.cancel();
        }
        lastToast =
                Toast.makeText(getApplicationContext(), text, duration);
        lastToast.show();
    }

    private void showError(String text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    private void onConnectedToService(BtService service) {
        btService = service;

        mTimer1 = new Runnable() {
            @Override
            public void run() {
                try {
                    btService.sendMsg(Protocol.RequestId.GET_DEBUG_BUFFER);
                } catch (IOException e) {
                    showError(e.toString());
                }
                timerHandler.postDelayed(this, 250);
            }
        };
        timerHandler.postDelayed(mTimer1, 250);
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        if (btService!=null) {
            try {
                btService.setDebugStreamId(position);
            } catch (IOException e) {
                showError(e.toString());
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plot);

//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            // Show the Up button in the action bar.
//            actionBar.setDisplayHomeAsUpEnabled(true);
//        }

        Spinner dropdown = findViewById(R.id.plot_type);
        String[] items = new String[]{"[DISABLED]", "Angle", "Pid Out", "Angle*10", "Motor Current", "Battery Voltage", "Battery Current", "ERPM/100"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);
        dropdown.setOnItemSelectedListener(this);

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

        Intent intent = new Intent(this, BtService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        timerHandler = new Handler();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BtService.Constants.MSG_DEBUG_INFO:
                        OnDebug(intent.getByteArrayExtra(BtService.Constants.DATA));
                        return;
                    default:
                        break;
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(
                        BtService.Constants.MSG_DEBUG_INFO));
    }

    @Override
    protected void onStart() {
        super.onStart();
        timerHandler.postDelayed(mTimer1, 500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        timerHandler.removeCallbacks(mTimer1);
    }

    private void OnDebug(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            series_.appendData(new DataPoint(last_point_data_++ , data[i]), false, max_point, true);
        }

        series_.appendData(new DataPoint(last_point_data_++ , data[data.length - 1]), true, max_point, false);
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        super.onDestroy();
    }

    private Runnable mTimer1;
    private Handler timerHandler;

    private LineGraphSeries<DataPoint> series_;
    private  int last_point_data_ = 0;

    private static final int max_point = 250;
}
