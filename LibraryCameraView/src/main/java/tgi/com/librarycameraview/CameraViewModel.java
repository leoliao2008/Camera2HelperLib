package tgi.com.librarycameraview;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
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

    Size getOptimalSupportedSize(CameraManager manager, String cameraId, int textureWidth, int textureHeight, int deviceRotation) throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedSizes = map.getOutputSizes(ImageFormat.JPEG);
        int compareWidth;
        int compareHeight;
        //卧倒的时候
        if (deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270) {
            compareWidth = textureWidth;
            compareHeight = textureHeight;
        } else {
            //直立的时候
            compareWidth = textureHeight;
            compareHeight = textureWidth;
        }
        float ratio = compareWidth * 1.0f / compareHeight;
        ArrayList<Size> fitsRatio = new ArrayList<>();
        ArrayList<Size> notFitsRatioAndSmaller = new ArrayList<>();
        for (Size size : supportedSizes) {
            if (size.getWidth() == compareWidth && size.getHeight() == compareHeight) {
                return size;
            } else {
                float tempRatio = size.getWidth() * 1.0f / size.getHeight();
                if (Math.abs(tempRatio - ratio) <= 0.01) {//差不多就行了，不可能尾数都一样
                    if (size.getWidth() >= compareWidth * 0.75
                            && size.getWidth() <= compareWidth * 1.25
                            && size.getHeight() >= compareHeight * 0.75
                            && size.getHeight() <= compareHeight * 1.25) {
                        fitsRatio.add(size);
                    }
                } else if (size.getWidth() <= compareWidth
                        && size.getHeight() <= compareHeight) {
                    notFitsRatioAndSmaller.add(size);
                }
            }
        }
        if (fitsRatio.size() > 0) {
            Size size = Collections.max(fitsRatio, new ComparatorBySize());
            showLog("fitsRatio.size() > 0 width=" + size.getWidth() + " height=" + size.getHeight(), 0);
            return size;
        }
        if (notFitsRatioAndSmaller.size() > 0) {
            Size size = Collections.min(notFitsRatioAndSmaller, new ComparatorByRatio(ratio));
            showLog("notFitsRatioAndSmaller.size() > 0 width=" + size.getWidth() + " height=" + size.getHeight(), 0);
            return size;
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

    int getTrueSensorOrientation(CameraManager manager, String cameraId, int deviceRotation) throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        Integer sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int degree = 0;
        switch (deviceRotation) {
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
        showLog("screen rotation= " + degree, 0);
        showLog("censor orientation = " + sensorOrientation, 0);
        return (sensorOrientation + degree + 360) % 360;
    }

    Matrix genPreviewTransformMatrix(Size supportedOptimalSize, Size actualDestSize, int deviceRotation, int sensorOrientation) {
        Matrix matrix = new Matrix();
        RectF beAppliedFrom = new RectF(0, 0, supportedOptimalSize.getHeight(), supportedOptimalSize.getWidth());
        RectF beAppliedTo = new RectF(0, 0, actualDestSize.getWidth(), actualDestSize.getHeight());


        beAppliedFrom.offset(beAppliedTo.centerX() - beAppliedFrom.centerX(),
                beAppliedTo.centerY() - beAppliedFrom.centerY());

        if (deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270) {
            matrix.setRectToRect(beAppliedFrom, beAppliedTo, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    actualDestSize.getWidth() * 1.0f / supportedOptimalSize.getWidth(),
                    actualDestSize.getHeight() * 1.0f / supportedOptimalSize.getHeight());
            matrix.postScale(scale,scale,beAppliedTo.centerX(), beAppliedTo.centerY());
        }
        matrix.postRotate(360-deviceRotation*90, beAppliedTo.centerX(), beAppliedTo.centerY());


        //        float scale;
        //        float scaleW = 1;
        //        float scaleH = 1;
        //
        //        switch (deviceRotation) {
        //            case Surface.ROTATION_0:
        //                scaleW = actualDestSize.getWidth() * 1.0f / supportedOptimalSize.getWidth();
        //                scaleH = actualDestSize.getHeight() * 1.0f / supportedOptimalSize.getHeight();
        //                break;
        //            case Surface.ROTATION_90:
        //                matrix.postRotate(270, beAppliedTo.centerX(), beAppliedTo.centerY());
        //                scaleW = actualDestSize.getHeight() * 1.0f / supportedOptimalSize.getWidth();
        //                scaleH = actualDestSize.getWidth() * 1.0f / supportedOptimalSize.getHeight();
        //                break;
        //            case Surface.ROTATION_180:
        //                matrix.postRotate(180, beAppliedTo.centerX(), beAppliedTo.centerY());
        //                scaleW = actualDestSize.getWidth() * 1.0f / supportedOptimalSize.getWidth();
        //                scaleH = actualDestSize.getHeight() * 1.0f / supportedOptimalSize.getHeight();
        //                break;
        //            case Surface.ROTATION_270:
        //                matrix.postRotate(90, beAppliedTo.centerX(), beAppliedTo.centerY());
        //                scaleW = actualDestSize.getHeight() * 1.0f / supportedOptimalSize.getWidth();
        //                scaleH = actualDestSize.getWidth() * 1.0f / supportedOptimalSize.getHeight();
        //                break;
        //        }
        //
        //        scale = Math.max(scaleW, scaleH);
        //        showLog("scale =" + scale, 1);
        //        matrix.postScale(scale, scale, beAppliedFrom.centerX(), beAppliedFrom.centerY());
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

    void createPreviewSession(final CameraDevice camera, final CameraCaptureSessionStateCallback callback, final Surface... outputSurfaces) throws CameraAccessException {

        camera.createCaptureSession(
                Arrays.asList(outputSurfaces),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            for (Surface surface : outputSurfaces) {
                                builder.addTarget(surface);
                            }
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            callback.onConfigured(builder, session);
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

    void showLog(String msg, int... logCode) {
        LogUtil.showLog(getClass().getSimpleName(), msg, logCode);
    }
}
