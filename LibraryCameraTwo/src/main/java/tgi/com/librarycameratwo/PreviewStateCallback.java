package tgi.com.librarycameratwo;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.support.annotation.NonNull;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public interface PreviewStateCallback {
    void onConfigured(CameraCaptureSession session,CaptureRequest.Builder builder);
    void onConfiguredFails(CameraCaptureSession session);
}
