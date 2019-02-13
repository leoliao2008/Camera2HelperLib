package com.tgi.cameraview;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 5/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>AndroidCameraDemo</i>
 * <p><b>Description:</b></p>
 */
class LogUtil {
    private static boolean DEBUG = false;

    private static final ArrayList<Integer> LOG_CODES = new ArrayList<>();

    static {
        LOG_CODES.addAll(Arrays.asList(0, 1, 3));
    }

    static void showLog(String msg, int... logCodes) {
        if (!DEBUG) {
            return;
        }
        for (int i : logCodes) {
            if (LOG_CODES.contains(i)) {
                Log.e("CameraView Debug", msg);
                break;
            }
        }

    }

    static void setDebugMode(boolean isDebugMode) {
        DEBUG = isDebugMode;
    }
}
