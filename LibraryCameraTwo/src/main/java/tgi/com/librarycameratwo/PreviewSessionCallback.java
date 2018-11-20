package tgi.com.librarycameratwo;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public interface PreviewSessionCallback {
    void onSessionEstablished(CaptureRequest.Builder builder, CameraCaptureSession session);
    void onFailToEstablishSession();
}
