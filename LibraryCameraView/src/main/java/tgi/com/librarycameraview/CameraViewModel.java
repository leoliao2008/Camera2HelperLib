package tgi.com.librarycameraview;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
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
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 4/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>AndroidCameraDemo</i>
 * <p><b>Description:</b></p>
 */
class CameraViewModel {

    Size getOptimalSupportedSize(CameraManager manager, String cameraId, int textureWidth, int textureHeight, int trueSensorOrientation) throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedTextureSizes = map.getOutputSizes(SurfaceTexture.class);
        int compareWidth;
        int compareHeight;
        //卧倒的时候
        if (trueSensorOrientation == 90 || trueSensorOrientation == 270) {
            compareWidth = textureWidth;
            compareHeight = textureHeight;
            showLog("卧倒的时候");
        } else {
            //直立的时候
            compareWidth = textureHeight;
            compareHeight = textureWidth;
            showLog("直立的时候");
        }
        float ratio = compareWidth * 1.0f / compareHeight;
        ArrayList<Size> fitsRatio = new ArrayList<>();
        ArrayList<Size> notFitsRatioAndSmaller = new ArrayList<>();
        for (Size size : supportedTextureSizes) {
            if (size.getWidth() == compareWidth && size.getHeight() == compareHeight) {
                return size;
            } else {
                if (size.getWidth() * 1.0f / size.getHeight() == ratio) {
                    fitsRatio.add(size);
                } else if (size.getWidth() <= compareWidth && size.getHeight() <= compareHeight) {
                    notFitsRatioAndSmaller.add(size);
                }
            }
        }
        if (fitsRatio.size() > 0) {
            return Collections.max(fitsRatio, new ComparatorByDeviation(ratio));
        }
        if (notFitsRatioAndSmaller.size() > 0) {
            return Collections.max(notFitsRatioAndSmaller, new ComparatorBySize());
        }
        return new Size(640, 480);
    }

    String getRearCameraId(CameraManager manager) throws CameraAccessException {
        String[] list = manager.getCameraIdList();
        for (String id : list) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            if (chars.get(CameraCharacteristics.LENS_FACING) == null) {
                continue;
            }
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return list[0];
    }

    int getTrueSensorOrientation(CameraManager manager, String cameraId, int screenOrientation) throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        Integer sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int degree = 0;
        switch (screenOrientation) {
            case Surface.ROTATION_0:
                degree = 0;
                break;
            case Surface.ROTATION_90:
                degree = 90;
                break;
            case Surface.ROTATION_180:
                degree = 180;
                break;
            case Surface.ROTATION_270:
                degree = 270;
                break;
        }
        showLog("screen rotation= "+degree);
        showLog("censor orientation = "+sensorOrientation);
//        return (sensorOrientation + degree) / 360;
        return degree;
    }

     Matrix getPreviewTransformMatrix(Size destSize, Size supportSize, int trueCameraOrientation) {
        Matrix matrix = new Matrix();
        RectF beAppliedTo = new RectF(0, 0, destSize.getWidth(), destSize.getHeight());
        RectF beAppliedWith = new RectF(0, 0, supportSize.getWidth(), supportSize.getHeight());
        beAppliedWith.offset(beAppliedTo.centerX() - beAppliedWith.centerX(),
                beAppliedTo.centerY() - beAppliedWith.centerY());
        matrix.setRectToRect(beAppliedWith,beAppliedTo, Matrix.ScaleToFit.START);
        matrix.postRotate(trueCameraOrientation,beAppliedTo.centerX(),beAppliedTo.centerY());
        float scale;
        float scaleW;
        float scaleH;
        //卧倒的时候
        if(trueCameraOrientation==90||trueCameraOrientation==270){
            scaleW=destSize.getWidth() * 1.0f / supportSize.getWidth();
            scaleH=destSize.getHeight() * 1.0f / supportSize.getHeight();
        }else {
            //直立的时候
            scaleW=destSize.getWidth() * 1.0f / supportSize.getHeight();
            scaleH=destSize.getHeight() * 1.0f / supportSize.getWidth();
        }
        scale=Math.max(scaleW,scaleH);
        matrix.postScale(scale,scale,beAppliedTo.centerX(),beAppliedTo.centerY());
        return matrix;
    }

    @SuppressLint("MissingPermission")
    void openCamera(CameraManager cameraManager, String cameraId,
                    CameraDevice.StateCallback callback, Handler handler) throws CameraAccessException {
        cameraManager.openCamera(
                cameraId,
                callback,
                handler
        );
    }

    void createPreviewSession(CameraDevice camera, final CameraCaptureSessionStateCallback callback, Surface... outputSurfaces) throws CameraAccessException {
        final CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        for (Surface surface : outputSurfaces) {
            builder.addTarget(surface);
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.build();
        camera.createCaptureSession(
                Arrays.asList(outputSurfaces),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        callback.onConfigured(builder, session);
                        try {
                            session.setRepeatingRequest(builder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                            callback.onError(e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        callback.onConfigureFailed(session);

                    }
                },
                null
        );
    }

    void showLog(String msg){
        LogUtil.showLog(getClass().getSimpleName(),msg);
    }
}
