package tgi.com.librarycameratwo;

import android.graphics.Bitmap;
import android.media.ImageReader;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public interface TakePicCallback {
    void onError(Exception e);
    void onImageTaken(Bitmap image);
}
