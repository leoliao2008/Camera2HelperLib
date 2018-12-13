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
class ComparatorBySize implements Comparator<Size> {


    @Override
    public int compare(Size o1, Size o2) {
        return Long.signum((long) o1.getWidth() * o1.getHeight() - (long) o2.getWidth() * o2.getHeight());
    }
}
