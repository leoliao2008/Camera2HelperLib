package tgi.com.librarycameratwo;

import android.util.Size;

import java.util.Comparator;
import java.util.SimpleTimeZone;

/**
 * Author: leo
 * Data: On 20/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public class CompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size first, Size second) {
        return first.getHeight()*first.getWidth()-second.getHeight()*second.getWidth();
    }
}
