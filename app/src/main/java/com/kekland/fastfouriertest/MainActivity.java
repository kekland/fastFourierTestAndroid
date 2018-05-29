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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    final int PERMISSION_REQUEST_CALLBACK = 25;

    Button toggleThreadButton;
    TextView volumeText;
    TextView peakText;
    ProgressBar volumeProgress;
    GraphView dataGraph;

    AudioRecorderThread thread;
    int indexOfThreadRun = 0;
    int indexToUpdateGraph = 2;

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
                                indexOfThreadRun++;
                                if(indexOfThreadRun % indexToUpdateGraph == 0) {
                                    fastFourierTransform.forward(buffer);
                                    DataPoint[] dataPoints = new DataPoint[200];

                                    float maxAmplitude = Float.MIN_VALUE;

                                    for(float sample : buffer) {
                                        maxAmplitude = (sample > maxAmplitude)? sample : maxAmplitude;
                                    }

                                    volumeProgress.setProgress(Math.round( 100 * maxAmplitude));
                                    volumeText.setText(Float.toString(maxAmplitude));

                                    float peak = -1f;
                                    int peakFreq = -1;
                                    for(int i = 0; i < 20000; i++) {
                                        float imag = fastFourierTransform.getFreq(i);
                                        if(imag > peak) {
                                            peak = imag;
                                            peakFreq = i;
                                        }
                                        if(i % 100 == 0) {
                                            dataPoints[i / 100] = new DataPoint(i, fastFourierTransform.getFreq(i));
                                        }
                                    }

                                    peakText.setText("Peaking at: " + peakFreq + "Hz");
                                    dataGraph.removeAllSeries();
                                    LineGraphSeries<DataPoint> series = new LineGraphSeries<>(dataPoints);
                                    dataGraph.addSeries(series);
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
        volumeText = findViewById(R.id.mainActivityTextVolume);
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
