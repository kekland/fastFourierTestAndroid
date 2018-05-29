package com.kekland.fastfouriertest;

/**
 * Created by kkerz on 29-May-18.
 */

public interface AudioRecorderInterface {
    void onDataReceive(float[] buffer);
}
