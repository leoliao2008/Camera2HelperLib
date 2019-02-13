package com.tgi.cameraview;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 5/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>AndroidCameraDemo</i>
 * <p><b>Description:</b></p>
 */
interface CameraCaptureSessionStateCallback {
    void onSessionEstablished(CaptureRequest.Builder builder, CameraCaptureSession session);

    void onFailToEstablishSession();
}
