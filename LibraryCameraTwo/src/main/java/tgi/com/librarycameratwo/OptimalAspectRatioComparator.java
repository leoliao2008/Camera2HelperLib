package tgi.com.librarycameratwo;

import android.util.Size;

import java.util.Comparator;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public class OptimalAspectRatioComparator implements Comparator<Size> {
    private float originRatio;

    public OptimalAspectRatioComparator(float originRatio) {
        this.originRatio = originRatio;
    }

    @Override
    public int compare(Size o1, Size o2) {
        float f1=Math.abs(o1.getWidth()*1.0f/o1.getHeight()-originRatio);
        float f2=Math.abs(o2.getWidth()*1.0f/o2.getHeight()-originRatio);
        return (int) (f2-f1);
    }
}
