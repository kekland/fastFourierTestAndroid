package com.kekland.fastfouriertest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    final int PERMISSION_REQUEST_CALLBACK = 25;

    Button toggleThreadButton;
    TextView peakText;
    ProgressBar volumeProgress;
    GraphView dataGraph;

    AudioRecorderThread thread;
    int indexOfThreadRun = 0;
    int indexToUpdateGraph = 1;

    FastFourierTransform fastFourierTransform = new FastFourierTransform(1024, 44100);

    public void toggleThread() {
        if(thread == null) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PERMISSION_REQUEST_CALLBACK);
            }
            else {
                thread = new AudioRecorderThread(new AudioRecorderInterface() {
                    @Override
                    public void onDataReceive(final float[] buffer) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //Make sure that everything runs in 30fps
                                indexOfThreadRun++;
                                if (indexOfThreadRun % indexToUpdateGraph == 0) {
                                    //Evaluate Fourier Transform
                                    fastFourierTransform.forward(buffer);

                                    //Get the volume
                                    float maxAmplitude = Float.MIN_VALUE;
                                    for (float sample : buffer) {
                                        maxAmplitude = (sample > maxAmplitude) ? sample : maxAmplitude;
                                    }
                                    volumeProgress.setProgress(Math.round(100 * maxAmplitude));

                                    float[] transformValues = new float[fastFourierTransform.specSize()];
                                    float transformMax = -1;
                                    float transformAvg = 0f;
                                    for (int i = 0; i < transformValues.length; i++) {
                                        float freq = (i / 1024f) * 44100f;
                                        float invVolume = (1f - (float)Math.sqrt((freq / 20000f)));
                                        transformValues[i] = fastFourierTransform.getBand(i) * invVolume;
                                        transformMax = (transformValues[i] > transformMax) ? transformValues[i] : transformMax;
                                        transformAvg += transformValues[i];
                                    }
                                    transformAvg /= transformValues.length;

                                    //Apply low pass filter below avg value
                                    /*for(int i =0; i < transformValues.length; i++) {
                                        if(transformValues[i] < transformAvg) {
                                            transformValues[i] = 0;
                                        }
                                    }*/

                                    DataPoint[] dataPoints = new DataPoint[transformValues.length];
                                    for (int i = 0; i < transformValues.length; i++) {
                                        dataPoints[i] = new DataPoint((i / 1024f) * 44100f, transformValues[i]);
                                    }
                                    dataGraph.removeAllSeries();
                                    LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
                                    dataGraph.addSeries(series);

                                    //Find peaks
                                    List<Integer> peaks = new ArrayList<>();
                                    for (int i = 0; i < transformValues.length - 2; i++) {
                                        float slopeFirst = transformValues[i + 1] - transformValues[i];
                                        float slopeSecond = transformValues[i + 2] - transformValues[i + 1];
                                        float slopeAvg = (slopeFirst - slopeSecond) / 2f;
                                        if (slopeFirst * slopeSecond <= 0) { // changed sign?
                                            if (slopeAvg > transformMax / 6f && transformValues[i + 1] > transformAvg) {
                                                peaks.add(i + 1);
                                            }
                                        }
                                    }

                                    //Fill String data about peaks
                                    StringBuilder peakData = new StringBuilder("Peaking at: \n");
                                    for (Integer peak : peaks) {
                                        Integer freq = Math.round((peak / 1024f) * 44100f);
                                        peakData.append(freq.toString());
                                        peakData.append("Hz\n");
                                    }
                                    peakText.setText(peakData.toString());
                                }
                            }
                        });

                    }
                }, 1, 44100);
            }
            toggleThreadButton.setText("Stop Thread");
        }
        else {
            thread.close();
            thread = null;
            toggleThreadButton.setText("Start Thread");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleThreadButton = findViewById(R.id.mainActivityButtonStartThread);
        peakText = findViewById(R.id.mainActivityTextPeak);
        volumeProgress = findViewById(R.id.mainActivityProgressVolume);
        dataGraph = findViewById(R.id.mainActivityGraphData);

        toggleThreadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleThread();
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CALLBACK: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    toggleThread();
                }
                else {
                    Toast.makeText(this, "Permission was not granted", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thread.close();
    }
}
