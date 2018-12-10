package tgi.com.librarycameraview;

import android.util.Size;

import java.util.Comparator;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 5/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>AndroidCameraDemo</i>
 * <p><b>Description:</b></p>
 */
public class ComparatorByRatio implements Comparator<Size> {
    private float mTargetValue;

    public ComparatorByRatio(float targetValue) {
        mTargetValue = targetValue;
    }

    @Override
    public int compare(Size o1, Size o2) {
        float f1 = Math.abs(o1.getWidth() * 1.0f / o1.getHeight()-mTargetValue);
        float f2 = Math.abs(o2.getWidth() * 1.0f / o2.getHeight()-mTargetValue);
        return f2 - f1 == 0 ? 0 : (f2 - f1 > 0 ? -1 : 1);
    }
}
