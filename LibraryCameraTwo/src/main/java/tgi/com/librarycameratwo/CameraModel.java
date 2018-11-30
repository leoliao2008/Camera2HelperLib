package tgi.com.librarycameratwo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_STATE_PRECAPTURE;
import static tgi.com.librarycameratwo.CameraViewConstant.INPUT_SIZE;
import static tgi.com.librarycameratwo.CameraViewConstant.MAX_PREVIEW_HEIGHT;
import static tgi.com.librarycameratwo.CameraViewConstant.MAX_PREVIEW_WIDTH;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_IMAGE_HAS_BEEN_TAKEN;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_LOCKING_FOCUS;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_PRECAPTURING_HAS_BEEN_STARTED;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_PREVIEWING;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_READY_TO_TAKE_PICTURE;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_WAITING_FOR_NON_PRECAPTURE_STATE;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_YOU_SHOULD_START_LOCKING_FOCUS;
import static tgi.com.librarycameratwo.CameraViewConstant.STAGE_YOU_SHOULD_START_PRECAPTURING;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public class CameraModel {
    private CameraManager mManager;
    private SparseIntArray mOrientationMapping;
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    private int mState;
    private int[] mRgbBytes;
    private Bitmap mRgbFrameBitmap;
    private Bitmap mCroppedBitmap;
    private Matrix mFrameToCropTransform;
    private Matrix mCropToFrameTransform;
    private byte[][] mYuvBytes;


    CameraModel(CameraManager cameraManager) {
        mManager = cameraManager;
        mOrientationMapping = new SparseIntArray();
        mOrientationMapping.append(Surface.ROTATION_0, 90);
        mOrientationMapping.append(Surface.ROTATION_90, 0);
        mOrientationMapping.append(Surface.ROTATION_180, 270);
        mOrientationMapping.append(Surface.ROTATION_270, 180);
    }

    String getFrontCamera() throws CameraAccessException {
        String[] cameraIdList = mManager.getCameraIdList();
        for (String id : cameraIdList) {
            CameraCharacteristics chars = mManager.getCameraCharacteristics(id);
            Integer integer = chars.get(CameraCharacteristics.LENS_FACING);
            if (integer != null && integer == CameraCharacteristics.LENS_FACING_FRONT) {
                return id;
            }
        }
        return null;
    }

    String getRearCamera() throws CameraAccessException {
        String[] cameraIdList = mManager.getCameraIdList();
        for (String id : cameraIdList) {
            CameraCharacteristics chars = mManager.getCameraCharacteristics(id);
            Integer integer = chars.get(CameraCharacteristics.LENS_FACING);
            if (integer != null && integer == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    void openCamera(String cameraId, CameraDevice.StateCallback callback, Handler handler) throws CameraAccessException {
        mManager.openCamera(cameraId, callback, handler);
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * minWidth and minHeight are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param minWidth  The minimum desired minWidth
     * @param minHeight The minimum desired minHeight
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    Size chooseOptimalSize(CameraCharacteristics chars, int minWidth, int minHeight) {
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] choices = map.getOutputSizes(SurfaceTexture.class);
        int minSize = Math.max(Math.min(minWidth, minHeight), CameraViewConstant.MINIMUM_PREVIEW_SIZE);
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
            Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            return chosenSize;
        } else {
            return choices[0];
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param textureViewFormerWidth  The width of `mTextureView`
     * @param textureViewFormerHeight The height of `mTextureView`
     */
    Matrix getTransformMatrix(
            int deviceRotation,
            int textureViewFormerWidth,
            int textureViewFormerHeight,
            Size targetSize) {
        Matrix matrix = new Matrix();
        RectF formerFrame = new RectF(0, 0, textureViewFormerWidth, textureViewFormerHeight);
        RectF targetFrame = new RectF(0, 0, targetSize.getHeight(), targetSize.getWidth());
        float centerX = formerFrame.centerX();
        float centerY = formerFrame.centerY();

        targetFrame.offset(centerX - targetFrame.centerX(), centerY - targetFrame.centerY());
        matrix.setRectToRect(formerFrame, targetFrame, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                (float) textureViewFormerHeight / targetSize.getHeight(),
                (float) textureViewFormerWidth / targetSize.getWidth());
        matrix.postScale(scale, scale, centerX, centerY);

        if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
            matrix.postRotate(90 * (deviceRotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == deviceRotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        return matrix;
    }


    /**
     * @param camera
     * @param previewSurface 用来展示preview画面的surface
     * @param callback
     * @param handler
     * @throws CameraAccessException
     */
    void startPreview(final CameraDevice camera,
                      final Surface previewSurface,
                      Surface stillPicCaptureSurface,
                      final Surface dynamicImageCaptureSurface,
                      final PreviewSessionCallback callback, final Handler handler) throws CameraAccessException {
        camera.createCaptureSession(
                Arrays.asList(previewSurface, stillPicCaptureSurface, dynamicImageCaptureSurface),
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
                            builder.addTarget(previewSurface);
                            builder.addTarget(dynamicImageCaptureSurface);

                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);


                            session.setRepeatingRequest(builder.build(), null, handler);
                            callback.onSessionEstablished(builder, session);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                            callback.onFailToEstablishSession();
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


    Bitmap getImageFromImageReader(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        image.close();
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }


    void takePic(final CaptureRequest.Builder builder, final CameraCaptureSession session,
                 final ImageReader imageReader, final int deviceRotation,
                 final Integer sensorOrientation, final TakePicCallback callback, final Handler handler) {
        //先设置获取图片后的回调。
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                showLog("onImageAvailable");
                Bitmap image = getImageFromImageReader(reader);
                if (image != null) {
                    imageReader.setOnImageAvailableListener(null, null);
                    //在这里处理照片方向和预览时方向不一致的问题。
                    int w1 = image.getWidth();
                    int h1 = image.getHeight();
                    int orientation = getJpegOrientation(deviceRotation, sensorOrientation);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(orientation, w1 / 2, h1 / 2);
                    //todo 先假设orientation都是90度
                    Bitmap bitmap = Bitmap.createBitmap(h1, w1, image.getConfig());
                    int w2 = bitmap.getWidth();
                    int h2 = bitmap.getHeight();
                    matrix.postTranslate((w2 - w1) / 2, (h2 - h1) / 2);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawBitmap(image, matrix, new Paint());
                    callback.onImageTaken(bitmap);
                } else {
                    callback.onError(new Exception("Image is taken but empty."));
                }
            }
        }, handler);
        //开始聚焦
        lockFocus(builder, session, new CameraCaptureSession.CaptureCallback() {
            private void process(CaptureResult result) {
                switch (mState) {
                    case STATE_PREVIEW: {
                        // We have nothing to do when the camera preview is working normally.
                        break;
                    }
                    case STATE_WAITING_LOCK: {//聚焦中。。。
                        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (afState == null) {
                            captureStillPicture(
                                    builder,
                                    session,
                                    imageReader,
                                    this,
                                    handler);
                        } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null ||
                                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                mState = STATE_PICTURE_TAKEN;
                                captureStillPicture(
                                        builder,
                                        session,
                                        imageReader,
                                        this,
                                        handler);
                            } else {
                                runPrecaptureSequence(
                                        builder,
                                        session,
                                        this,
                                        handler);
                            }
                        }
                        break;
                    }
                    case STATE_WAITING_PRECAPTURE: {//等待闪光灯
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                            mState = STATE_WAITING_NON_PRECAPTURE;
                        }
                        break;
                    }
                    case STATE_WAITING_NON_PRECAPTURE: {//闪光灯准备好了
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture(
                                    builder,
                                    session,
                                    imageReader,
                                    this,
                                    handler);
                        }
                        break;
                    }
                }
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                process(partialResult);
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                process(result);
            }
        }, handler);
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus(CaptureRequest.Builder builder,
                           CameraCaptureSession session,
                           CameraCaptureSession.CaptureCallback callback,
                           Handler handler) {
        try {
            // This is how to tell the camera to lock focus.
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            session.capture(builder.build(), callback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus(CaptureRequest.Builder builder,
                             CameraCaptureSession session,
                             CameraCaptureSession.CaptureCallback callback,
                             Handler handler) {
        try {
            // Reset the auto-focus trigger
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            session.capture(builder.build(), callback, handler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            session.setRepeatingRequest(builder.build(), callback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence(CaptureRequest.Builder builder,
                                       CameraCaptureSession session,
                                       CameraCaptureSession.CaptureCallback callback,
                                       Handler handler) {
        try {
            // This is how to tell the camera to trigger.
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            session.capture(builder.build(), callback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void captureStillPicture(
            final CaptureRequest.Builder builder,
            CameraCaptureSession session,
            ImageReader imageReader,
            final CameraCaptureSession.CaptureCallback callback,
            final Handler handler
    ) {
        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);


            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus(builder, session, callback, handler);
                }
            };
            session.stopRepeating();
            session.abortCaptures();
            session.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * https://stackoverflow.com/questions/48406497/camera2-understanding-the-sensor-and-device-orientations
     *
     * @param deviceRotation
     * @return
     */
    private int getJpegOrientation(int deviceRotation, int cameraSensorOrientation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from mOrientationMapping.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        int orientation = (mOrientationMapping.get(deviceRotation) + cameraSensorOrientation + 270) % 360;
        showLog("getJpegOrientation: " + orientation);
        return orientation;
    }

    void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
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


    private void showLog(String msg) {
        if (CameraViewConstant.IS_DEBUG_MODE) {
            Log.e(getClass().getSimpleName(), msg);
        }
    }

    Integer getCameraSensorOrientation(CameraDevice camera) throws CameraAccessException {
        return mManager.getCameraCharacteristics(camera.getId()).get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    CameraCharacteristics getCameraCharacteristics(CameraDevice camera) throws CameraAccessException {
        return mManager.getCameraCharacteristics(camera.getId());
    }

    void initTensorFlowInput(Size optimalSize) {
        int width = optimalSize.getWidth();
        int height = optimalSize.getHeight();

        mRgbBytes = new int[width * height];
        mRgbFrameBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

        //先假设都是90度sensor orientation
        mFrameToCropTransform = ImageUtils.getTransformationMatrix(
                width, height,
                INPUT_SIZE, INPUT_SIZE,
                90, true);

        mCropToFrameTransform = new Matrix();
        mFrameToCropTransform.invert(mCropToFrameTransform);
        mYuvBytes = new byte[3][];
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
        ImageUtils.convertYUV420ToARGB8888(
                mYuvBytes[0],
                mYuvBytes[1],
                mYuvBytes[2],
                width,
                height,
                yRowStride,
                uvRowStride,
                uvPixelStride,
                mRgbBytes);

        mRgbFrameBitmap.setPixels(mRgbBytes, 0, width, 0, 0, width, height);
        final Canvas canvas = new Canvas(mCroppedBitmap);
        canvas.drawBitmap(mRgbFrameBitmap, mFrameToCropTransform, null);
        image.close();
        return mCroppedBitmap;
    }
}
