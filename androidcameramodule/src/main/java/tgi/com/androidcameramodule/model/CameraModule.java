package tgi.com.androidcameramodule.model;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
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
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
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
public class CameraModule {
    private CameraManager mCameraManager;

    public CameraModule(CameraManager cameraManager) {
        mCameraManager = cameraManager;
    }

    @SuppressLint("MissingPermission")
    public void openMainCamera(
            String cameraId,
            final Surface preView,
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
                                    add(preView);
                                    if (reprocessSurface != null) {
                                        add(reprocessSurface);
                                    }
                                }
                            };
                            final CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(preView);
                            camera.createCaptureSession(
                                    outputs,
                                    new CameraCaptureSession.StateCallback() {
                                        @Override
                                        public void onConfigured(@NonNull CameraCaptureSession session) {
                                            //auto focus
                                            builder.set(
                                                    CaptureRequest.CONTROL_AF_MODE,
                                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                            );
                                            //auto flash
                                            builder.set(
                                                    CaptureRequest.CONTROL_AE_MODE,
                                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                                            );
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
                                            camera.close();
                                            callback.onError("createCaptureSession->onConfigureFailed: "+session.toString());

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
                        callback.onError("Camera Fails to open, error code: " + error);

                    }
                },
                handler
        );
    }

    public Matrix adjustPreviewMatrixDueToRotation(
            int originWidth,
            int originHeight,
            int optimizedWidth,
            int optimizedHeight,
            int screenRotation){
        RectF widgetBound=new RectF(0,0,originWidth,originHeight);
        RectF previewBound=new RectF(0,0,optimizedHeight,optimizedWidth);//switch the width and height value
        float x1 = widgetBound.centerY();
        float y1 = widgetBound.centerX();
        float x2= previewBound.centerX();
        float y2 = previewBound.centerY();
        previewBound.offset(x1-x2,y1-y2);
        Matrix matrix=new Matrix();
        matrix.setRectToRect(widgetBound,previewBound, Matrix.ScaleToFit.FILL);
        float scale=Math.max(originWidth*1.0f/optimizedWidth,originHeight*1.0f/optimizedHeight);
        matrix.postScale(scale,scale,x1,y1);
        if(screenRotation==Surface.ROTATION_90){
            matrix.postRotate(-90,x1,y1);
        }else if(screenRotation==Surface.ROTATION_270){
            matrix.postRotate(90,x1,y1);
        }else if(screenRotation==Surface.ROTATION_180){
            matrix.postRotate(-180,x1,y1);
        }
        return matrix;
    }


    public String getMainCameraId() {
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            for (String id : cameraIdList) {
                CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(id);
                //we need this to get the supported image sizes
                StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
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

    public Size getOptimizedSize(Size maxiMumSize, String cameraId) {
        Size result = null;
        try {
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
//            final float maximumRatio=maxiMumSize.getWidth() * 1.0f / maxiMumSize.getHeight();
            showLog("max width= "+maxiMumSize.getWidth()+" max height= "+maxiMumSize.getHeight());
            ArrayList<Size> bigEnough=new ArrayList<>();
            for(Size temp:sizes){
                int width = temp.getWidth();
                int height = temp.getHeight();
                showLog("sample with= "+width+" sample height= "+height);
                if(width>=maxiMumSize.getWidth()&&height>=maxiMumSize.getHeight()){
                    bigEnough.add(temp);
                }
            }
            result = Collections.min(
                    bigEnough,
                    new Comparator<Size>() {
                        @Override
                        public int compare(Size first, Size second) {
                            return first.getWidth() * first.getHeight() - second.getWidth() * second.getHeight();
                        }
                    }
            );
            showLog("result width="+result.getWidth()+" height="+result.getHeight());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void showLog(String msg){
        Log.e(getClass().getSimpleName(),msg);
    }

}
