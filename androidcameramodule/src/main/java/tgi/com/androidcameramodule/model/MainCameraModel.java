package tgi.com.androidcameramodule.model;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;

import tgi.com.androidcameramodule.callback.PreviewCallback;
import tgi.com.androidcameramodule.utils.CameraPermissionHelper;

/**
 * This model is with reference to https://blog.csdn.net/CrazyMo_/article/details/78182243
 */
public class MainCameraModel {
    private CameraManager mCameraManager;

    public MainCameraModel(CameraManager cameraManager) {
        mCameraManager = cameraManager;
    }

    @SuppressLint("MissingPermission")
    public void openMainCamera(
            String cameraId,
            final Surface previewSurface,
            final PreviewCallback callback,
            @Nullable final Surface reprocessSurface,
            @Nullable final Handler handler) throws CameraAccessException, IllegalArgumentException, SecurityException {

        mCameraManager.openCamera(
                cameraId,
                new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull final CameraDevice camera) {
                        callback.onCameraOpen(camera);
                        try {
                            final List<Surface> outputs = new ArrayList<Surface>() {
                                {
                                    add(previewSurface);
                                    if (reprocessSurface != null) {
                                        add(reprocessSurface);
                                    }
                                }
                            };
                            final CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(previewSurface);
                            camera.createCaptureSession(
                                    outputs,
                                    new CameraCaptureSession.StateCallback() {
                                        @Override
                                        public void onConfigured(@NonNull CameraCaptureSession session) {
                                            builder.set(
                                                    CaptureRequest.CONTROL_AF_MODE,
                                                    CaptureRequest.CONTROL_AF_MODE_AUTO);
                                            try {
                                                session.setRepeatingRequest(
                                                        builder.build(),
                                                        null,
                                                        handler
                                                );
                                            } catch (CameraAccessException e) {
                                                e.printStackTrace();
                                            }

                                        }

                                        @Override
                                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                        }
                                    },
                                    handler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        camera.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        camera.close();
                        callback.onError("Camera Fails to open, error code: "+error);

                    }
                },
                handler
        );
    }


    public String getMainCameraId() {
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            for (String id : cameraIdList) {
                CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(id);
                //we need this to get the supported image sizes
                StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(map==null){
                    continue;
                }
                Integer direction = chars.get(CameraCharacteristics.LENS_FACING);
                if (direction != null && direction == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Size getClosestSupportedSize(Size parentSize, String cameraId, int format){
        Size result=null;
        try {
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(format);
            final float parentRatio=parentSize.getWidth()*1.0f/parentSize.getHeight();
            result = Collections.max(
                    Arrays.asList(sizes),
                    new Comparator<Size>() {
                        @Override
                        public int compare(Size first, Size second) {
                            float r1 = Math.abs(first.getWidth() * 1.0f / first.getHeight() - parentRatio);
                            float r2 = Math.abs(second.getWidth() * 1.0f / second.getHeight() - parentRatio);
                            return (int) (r2 - r1);
                        }
                    }
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return result;
    }

}
