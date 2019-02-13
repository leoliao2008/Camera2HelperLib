package com.tgi.cameraview;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

    //以下变量都是tensorFlow需要的
    private int[] mRgbBytes;
    private Bitmap mRgbFrameBitmap;
    private Bitmap mCroppedBitmap;
    private Matrix mFrameToCropTransform;
    private byte[][] mYuvBytes;

    Size getOptimalSupportedPreviewSize(CameraManager manager, String cameraId, int targetWidth, int targetHeight) throws CameraAccessException {
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] supportedSizes = map.getOutputSizes(SurfaceTexture.class);
        float ratio = targetWidth * 1.0f / targetHeight;
        ArrayList<Size> fitsRatio = new ArrayList<>();
        ArrayList<Size> notFitsRatioAndSmaller = new ArrayList<>();
        for (Size size : supportedSizes) {
            if (size.getWidth() == targetWidth && size.getHeight() == targetHeight) {
                return size;
            } else {
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

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * minWidth and minHeight are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param minWidth  The minimum desired minWidth
     * @param minHeight The minimum desired minHeight
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    Size getTensorFlowOptimalSize(CameraCharacteristics chars, int minWidth, int minHeight) {
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);
        int minSize = Math.max(Math.min(minWidth, minHeight), CONSTANTS.MINIMUM_PREVIEW_SIZE);
        Size desiredSize = new Size(minWidth, minHeight);
        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        List<Size> bigEnough = new ArrayList<Size>();
        List<Size> tooSmall = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }
            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        if (exactSizeFound) {
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            Size chosenSize = Collections.min(bigEnough, new ComparatorBySize());
            return chosenSize;
        } else {
            return choices[0];
        }
    }

    void initTensorFlowInput(Size optimalSize, int deviceOrientation) {
        int width = optimalSize.getWidth();
        int height = optimalSize.getHeight();

        mRgbBytes = new int[width * height];
        mRgbFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCroppedBitmap = Bitmap.createBitmap(CONSTANTS.INPUT_SIZE, CONSTANTS.INPUT_SIZE, Bitmap.Config.ARGB_8888);

        //经真机实测得出的逻辑
        int degreeToRote = 0;
        switch (deviceOrientation) {
            case Surface.ROTATION_0:
                degreeToRote = 90;
                break;
            case Surface.ROTATION_90:
                degreeToRote = 0;
                break;
            case Surface.ROTATION_180:
                degreeToRote = 270;
                break;
            case Surface.ROTATION_270:
                degreeToRote = 180;
                break;
        }

        mFrameToCropTransform = getTensorFlowTransformationMatrix(
                width, height,
                CONSTANTS.INPUT_SIZE, CONSTANTS.INPUT_SIZE,
                degreeToRote, true);

        Matrix cropToFrameTransform = new Matrix();
        mFrameToCropTransform.invert(cropToFrameTransform);
        mYuvBytes = new byte[3][];
    }

    private Matrix getTensorFlowTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }

    Bitmap getImageFromYUV_420_888Format(Image image, Size optimalSize) {
        int width = optimalSize.getWidth();
        int height = optimalSize.getHeight();
        if (image == null) {
            return null;
        }
        Image.Plane[] planes = image.getPlanes();
        if (planes == null) {
            return null;
        }
        fillBytes(planes, mYuvBytes);

        int yRowStride = planes[0].getRowStride();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();
        convertYUV420ToARGB8888(
                mYuvBytes[0],
                mYuvBytes[1],
                mYuvBytes[2],
                width,
                height,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                mRgbBytes);
        //下面的操作比较耗时，在这里可以早点释放image
        image.close();

        mRgbFrameBitmap.setPixels(mRgbBytes, 0, width, 0, 0, width, height);
        final Canvas canvas = new Canvas(mCroppedBitmap);
        canvas.drawBitmap(mRgbFrameBitmap, mFrameToCropTransform, null);
        return mCroppedBitmap;
    }

    private void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    private void convertYUV420ToARGB8888(byte[] yData, byte[] uData, byte[] vData, int width, int height,
                                         int yRowStride, int uvRowStride, int uvPixelStride, int[] out) {
        int i = 0;
        for (int y = 0; y < height; y++) {
            int pY = yRowStride * y;
            int uv_row_start = uvRowStride * (y >> 1);
            int pU = uv_row_start;
            int pV = uv_row_start;

            for (int x = 0; x < width; x++) {
                int uv_offset = (x >> 1) * uvPixelStride;
                out[i++] = YUV2RGB(
                        convertByteToInt(yData, pY + x),
                        convertByteToInt(uData, pU + uv_offset),
                        convertByteToInt(vData, pV + uv_offset));
            }
        }
    }

    private int convertByteToInt(byte[] arr, int pos) {
        return arr[pos] & 0xFF;
    }

    private int YUV2RGB(int nY, int nU, int nV) {
        nY -= 16;
        nU -= 128;
        nV -= 128;
        if (nY < 0)
            nY = 0;

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);

        int nR = (int) (1192 * nY + 1634 * nV);
        int nG = (int) (1192 * nY - 833 * nV - 400 * nU);
        int nB = (int) (1192 * nY + 2066 * nU);

        int kMaxChannelValue = 262143;
        nR = Math.min(kMaxChannelValue, Math.max(0, nR));
        nG = Math.min(kMaxChannelValue, Math.max(0, nG));
        nB = Math.min(kMaxChannelValue, Math.max(0, nB));

        nR = (nR >> 10) & 0xff;
        nG = (nG >> 10) & 0xff;
        nB = (nB >> 10) & 0xff;

        return 0xff000000 | (nR << 16) | (nG << 8) | nB;
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
            matrix.postScale(scaleX, scaleY, beAppliedTo.centerX(), beAppliedTo.centerY());
        }

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
                              final Surface stillPicSurface,
                              final Surface tensorFlowSurface) throws CameraAccessException {

        camera.createCaptureSession(
                Arrays.asList(preViewSurface, stillPicSurface, tensorFlowSurface),//这里需要把用到的surface都加进来，否则surface今后获取不到图像。
                new CameraCaptureSession.StateCallback() {
                    AtomicBoolean isFirstOnConfigured = new AtomicBoolean(true);

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
                            builder.addTarget(tensorFlowSurface);
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            if (isFirstOnConfigured.compareAndSet(true, false)) {
                                callback.onSessionEstablished(builder, session);
                            }
                            session.setRepeatingRequest(builder.build(), null, handler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        callback.onFailToEstablishSession();

                    }
                },
                handler
        );
    }

    void showLog(String msg, int... logCode) {
        LogUtil.showLog(msg, logCode);
    }

    Bitmap getBitmapFromJpegFormat(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        byte[] temp = new byte[buffer.remaining()];
        buffer.get(temp);
        return BitmapFactory.decodeByteArray(temp, 0, temp.length);
    }

}
