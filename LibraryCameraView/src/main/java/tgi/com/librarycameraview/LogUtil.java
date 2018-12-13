package tgi.com.librarycameraview;

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
    private static final ArrayList<Integer> LOG_CODES = new ArrayList<>();

    static {
        LOG_CODES.addAll(Arrays.asList(0, 1, 3));
    }

    static void showLog(String tag, String msg, int... logCodes) {
        for (int i : logCodes) {
            if (LOG_CODES.contains(i)) {
                Log.e("Error By Leo: " + tag, msg);
                break;
            }
        }
    }
}
