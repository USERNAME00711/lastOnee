package com.example.lastone;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.samsung.sdc22.health.advanced.Manifest;
import com.samsung.sdc22.health.advanced.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private final static String APP_TAG = "MainActivity";
    private final static int MEASUREMENT_DURATION = 60000;
    private final static int MEASUREMENT_TICK = 200;

    private final AtomicBoolean isMeasurementRunning = new AtomicBoolean(false);
    private TextView txtHeartRate;
    private TextView txtStatus;
    private Button butStart;
    private CircularProgressIndicator measurementProgress = null;

    private SensorManager mSensorManager;
    private Sensor ppg;

    private static int count;
    private static double time = -0.02;

    List<Float> ppgSignal = new ArrayList<>();
    Python py;
    private static int bgl;

    final CountDownTimer countDownTimer = new CountDownTimer(MEASUREMENT_DURATION, MEASUREMENT_TICK) {
        @Override
        public void onTick(long timeLeft) {
            if (isMeasurementRunning.get()) {
                txtStatus.setText("Measuring...");
                measurementProgress.setProgress(measurementProgress.getProgress() + 1, true);
            } else {
                cancel();
            }
        }

        @Override
        public void onFinish() {
            runOnUiThread(() -> {
                txtStatus.setText("Measurement finished");
                butStart.setText("yoo");
                measurementProgress.invalidate();
            });
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            isMeasurementRunning.set(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtHeartRate = findViewById(R.id.txtHeartRate);
        txtStatus = findViewById(R.id.txtStatus);
        butStart = findViewById(R.id.butStart);
        measurementProgress = findViewById(R.id.progressBar);

        count = 0;
        adjustProgressBar(measurementProgress);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);


    }

    @Override
    protected void onResume() {
        super.onResume();
        count = 0;
        time = -0.02;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    void adjustProgressBar(CircularProgressIndicator progressBar) {
        int padding = 1;
        progressBar.setPadding(padding, padding, padding, padding);
        int trackThickness = progressBar.getTrackThickness();
        int progressBarSize = getResources().getDisplayMetrics().widthPixels - trackThickness - 2 * padding;
        progressBar.setIndicatorSize(progressBarSize);
    }

    public void performMeasurement(View view) {
        count = 0;
        time = -0.02;

        if (!isMeasurementRunning.get()) {
            butStart.setText("yoo");
            measurementProgress.setProgress(0);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            isMeasurementRunning.set(true);
            new Thread(countDownTimer::start).start();
            ppg = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

            if (ppg == null) {
                Toast.makeText(getApplicationContext(), "PPG sensor not available", Toast.LENGTH_SHORT).show();
                return;
            }

            mSensorManager.registerListener(mLightSensorListener, ppg, SensorManager.SENSOR_DELAY_GAME);

        } else {
            butStart.setEnabled(false);
            isMeasurementRunning.set(false);
            mSensorManager.unregisterListener(mLightSensorListener);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                butStart.setText("yoo");
                txtStatus.setText("Press Measure to initiate Measurement");
                mSensorManager.unregisterListener(mLightSensorListener);
                measurementProgress.setProgress(0);
                butStart.setEnabled(true);
            }, MEASUREMENT_TICK * 2);
        }
    }

    private SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Float value = event.values[0];
            time += 0.02;
            count++;
            if (count == 1800) {
                runPython();
                mSensorManager.unregisterListener(mLightSensorListener);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public void runPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        py = Python.getInstance();
        Object[] objects = ppgSignal.toArray();
        PyObject array = PyObject.fromJava(objects);
        String textPy = py.getModule("script").callAttr("process_signal", array).toString();
        bgl = Integer.parseInt(textPy);
        txtHeartRate.setText(String.valueOf(bgl));
    }
}
