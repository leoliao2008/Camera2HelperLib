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
import android.support.annotation.Nullable;
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

    Size getOptimalSupportedSize(CameraManager manager, String cameraId, int targetWidth, int targetHeight) throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedSizes = map.getOutputSizes(ImageFormat.JPEG);
        float ratio = targetWidth * 1.0f / targetHeight;
        ArrayList<Size> fitsRatio = new ArrayList<>();
        ArrayList<Size> notFitsRatioAndSmaller = new ArrayList<>();
        for (Size size : supportedSizes) {
            if (size.getWidth() == targetWidth && size.getHeight() == targetHeight) {
                return size;
            } else {
                //                if (size.getWidth() < targetWidth * 0.75
                //                        || size.getWidth() > targetWidth * 1.25
                //                        || size.getHeight() < targetHeight * 0.75
                //                        || size.getHeight() > targetHeight * 1.25) {
                //                    continue;//把太大或太小的过滤掉
                //                }
                if (size.getWidth() > targetWidth || size.getHeight() > targetHeight) {
                    continue;//把大尺寸过滤掉
                }
                float tempRatio = size.getWidth() * 1.0f / size.getHeight();
                if (Math.abs(tempRatio - ratio) <= 0.01) {//差不多就行了，不可能尾数都一样
                    fitsRatio.add(size);
                } else {
                    notFitsRatioAndSmaller.add(size);
                }
            }
        }
        if (fitsRatio.size() > 0) {
            return Collections.max(fitsRatio, new ComparatorBySize());
        }
        if (notFitsRatioAndSmaller.size() > 0) {
            return Collections.max(notFitsRatioAndSmaller, new ComparatorBySize());
        }
        //凑合找最大的
        return supportedSizes[0];
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
        return (sensorOrientation + degree + 360) % 360;
    }

    Matrix genPreviewTransformMatrix(Size supportedOptimalSize, Size actualDestSize, int deviceRotation) {
        Matrix matrix = new Matrix();
        RectF beAppliedFrom;
        //根据当前手机旋转方向判断摄像头的视图区域的形状
        if (deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270) {
            beAppliedFrom = new RectF(0, 0, supportedOptimalSize.getWidth(), supportedOptimalSize.getHeight());
        } else {
            beAppliedFrom = new RectF(0, 0, supportedOptimalSize.getHeight(), supportedOptimalSize.getWidth());
        }
        //这里不用判断texture view是横屏还是竖屏，在传入来时已经判断了。
        RectF beAppliedTo = new RectF(0, 0, actualDestSize.getWidth(), actualDestSize.getHeight());

        //把摄像头内容移动到视图中心
        beAppliedFrom.offset(beAppliedTo.centerX() - beAppliedFrom.centerX(),
                beAppliedTo.centerY() - beAppliedFrom.centerY());

        // 旋转摄像头图像的方向，这里是根据真机上的测试调整的，貌似当前设备旋转角度+补充旋转角度=360.
        // 当前设备旋转角度要乘以90是因为要转化成真实角度，否则是1/2/3/0。360减去设备旋转角度，得到的是需要补充旋转的角度。
        matrix.postRotate(360 - deviceRotation * 90, beAppliedTo.centerX(), beAppliedTo.centerY());
        float scaleX;
        float scaleY;
        if (deviceRotation == Surface.ROTATION_90 || deviceRotation == Surface.ROTATION_270) {
            //真机上测试时，发现横屏时的图像比例不正常（图像上下拉伸，同时左右两边到视图边缘留有大片空隙）。这里要根据比例调整，填充屏幕。
            //为什么是此宽除以彼长，此长除以彼宽？因为刚刚图像旋转了90度/270度。
            scaleX = actualDestSize.getWidth() * 1.0f / supportedOptimalSize.getHeight();
            scaleY = actualDestSize.getHeight() * 1.0f / supportedOptimalSize.getWidth();
        } else {
            scaleX =actualDestSize.getWidth()*1.0f/supportedOptimalSize.getWidth();
            scaleY=actualDestSize.getHeight()*1.0f/supportedOptimalSize.getHeight();
        }
        matrix.postScale(scaleX, scaleY, beAppliedTo.centerX(), beAppliedTo.centerY());
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

    void createPreviewSession(final CameraDevice camera,
                              final CameraCaptureSessionStateCallback callback,
                              @Nullable final Handler handler,
                              final Surface preViewSurface,
                              final Surface stillPicSurface) throws CameraAccessException {

        camera.createCaptureSession(
                Arrays.asList(preViewSurface, stillPicSurface),//这里需要把用到的surface都加进来，否则surface今后获取不到图像。
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            //这里有坑，之前以为：target一次只能有一个，如果设置两个以上会非常卡顿。
                            //但实际上：previewSurface输入的是textureSurface的surface,它会自动处理掉接受到的image，使其自动close()，循环使用。
                            //然而其它的target接受到image后，
                            //并不会自动处理掉image，不会调用image.close()，因此最初几个图像被接收后，后面的会卡住。
                            //正确的做法是每个surface被addTarget()前，先设置onImageAvailableListener，在回调中手动处理image即可。
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            //这里不能把stillPicSurface加到target中去，因为stillPicSurface的图片格式是jpeg，不适用TEMPLATE_PREVIEW
                            builder.addTarget(preViewSurface);
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            callback.onConfigured(builder, session);
                            session.setRepeatingRequest(builder.build(), null, handler);
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
                handler
        );
    }

    void showLog(String msg, int... logCode) {
        LogUtil.showLog(getClass().getSimpleName(), msg, logCode);
    }
}
