package tgi.com.librarycameratwo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
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
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_STATE_PRECAPTURE;
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
    private String mCurrentStage = CameraViewConstant.STAGE_PREVIEWING;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Integer mSensorOrientation;


    public CameraModel(CameraManager cameraManager) {
        mManager = cameraManager;
    }

    public String getFrontCamera() throws CameraAccessException {
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

    public String getRearCamera() throws CameraAccessException {
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
    public void openCamera(String cameraId, CameraDevice.StateCallback callback, Handler handler) throws CameraAccessException {

        mManager.openCamera(cameraId, callback, handler);
    }


    public Size getOptimalPreviewSize(int originPreviewWidth, int originPreviewHeight, String cameraId)
            throws CameraAccessException {
        StreamConfigurationMap map = mManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        float originRatio = originPreviewWidth * 1.0f / originPreviewHeight;
        //todo     for now it will do, but must be improved later
        return Collections.max(
                Arrays.asList(sizes),
                new OptimalAspectRatioComparator(originRatio)
        );
    }


    /**
     *
     * @param camera
     * @param previewSurface 用来展示preview画面的surface
     * @param outputs 一组surface，对应不同的request，在请求前必须先把surface放进这里。
     * @param callback
     * @param handler
     * @throws CameraAccessException
     */
    public void startPreview(final CameraDevice camera, final Surface previewSurface, final List<Surface> outputs,
                             final PreviewSessionCallback callback, final Handler handler) throws CameraAccessException {
        mSensorOrientation = mManager.getCameraCharacteristics(camera.getId()).get(CameraCharacteristics.SENSOR_ORIENTATION);
        camera.createCaptureSession(
                outputs,
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            //这里有坑，target一次只能有一个，如果设置两个以上会非常卡顿。
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.addTarget(previewSurface);

                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);


                            session.setRepeatingRequest(builder.build(),null, handler);
                            callback.onSessionEstablished(builder,session);
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

    private Size setUpCameraOutputs(Display display,String cameraId,int width, int height) throws CameraAccessException {
        // For still image captures, we use the largest available size.
        StreamConfigurationMap map = mManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size largest = Collections.max(
                Arrays.asList(sizes),
                new CompareSizesByArea());

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = display.getRotation();
        //noinspection ConstantConditions
        mSensorOrientation = mManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                break;
        }

        Point displaySize = new Point();
        display.getSize(displaySize);
        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;

        if (swappedDimensions) {
            rotatedPreviewWidth = height;
            rotatedPreviewHeight = width;
            maxPreviewWidth = displaySize.y;
            maxPreviewHeight = displaySize.x;
        }

        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }

        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }

        // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
        // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
        // garbage capture data.
        return chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, largest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
//        int orientation = getResources().getConfiguration().orientation;
//        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
//            mTextureView.setAspectRatio(
//                    mPreviewSize.getWidth(), mPreviewSize.getHeight());
//        } else {
//            mTextureView.setAspectRatio(
//                    mPreviewSize.getHeight(), mPreviewSize.getWidth());
//        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }



    private Matrix configureTransform(Display display,int surfaceViewWidth,
                                      int surfaceViewHeight,Size optimalPreviewSize) {
        //todo this is for reset matrix for surface view
        int rotation = display.getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, surfaceViewWidth, surfaceViewHeight);
        RectF bufferRect = new RectF(0, 0, optimalPreviewSize.getHeight(), optimalPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) surfaceViewHeight / optimalPreviewSize.getHeight(),
                    (float) surfaceViewWidth / optimalPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        return matrix;
    }

    private Bitmap getImageFromImageReader(ImageReader reader) {
        Image image = reader.acquireNextImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        image.close();
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    public void takePic(final CaptureRequest.Builder builder, final CameraCaptureSession session,
                        final ImageReader imageReader, final TakePicCallback callback, final Handler handler)
            throws CameraAccessException {
        mCurrentStage = STAGE_YOU_SHOULD_START_LOCKING_FOCUS;
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                showLog("onImageAvailable");
                Bitmap image = getImageFromImageReader(reader);
                if(image!=null){
                    callback.onImageTaken(image);
                }else {
                    callback.onError(new Exception("Image is taken but empty."));
                }
                //do not setOnImageAvailableListener(null,null) here, will cause subsequent pics fail to take.
//                imageReader.setOnImageAvailableListener(null,null);
            }
        }, handler);
        session.setRepeatingRequest(
                builder.build(),
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                                 @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                        super.onCaptureStarted(session, request, timestamp, frameNumber);
                        showLog("onCaptureStarted:");
                    }

                    @Override
                    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                    @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                        super.onCaptureProgressed(session, request, partialResult);
                        showLog("onCaptureProgressed:");
                        //this is the beginning of the process
                        process(session, request, partialResult);
                    }

                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        mCurrentStage = STAGE_IMAGE_HAS_BEEN_TAKEN;
                        showLog("onCaptureCompleted:");
                        process(session, request, result);

                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                        showLog("onCaptureFailed:");
                        process(session,request,null);
                    }

                    private void process(@NonNull CameraCaptureSession session,
                                         @NonNull CaptureRequest request, @Nullable CaptureResult partialResult) {
                        switch (whatStageNow(partialResult)) {
                            case STAGE_YOU_SHOULD_START_LOCKING_FOCUS:
                                try {
                                    lockFocus(session, builder, this, handler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    callback.onError(e);
                                }
                                break;
                            case STAGE_YOU_SHOULD_START_PRECAPTURING:
                                try {
                                    startPreCapture(session, builder, this, handler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    callback.onError(e);
                                }
                                break;
                            case STAGE_READY_TO_TAKE_PICTURE:
                                try {
                                    captureStillPic(session, imageReader, this, handler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    callback.onError(e);
                                }
                                break;
                            case STAGE_IMAGE_HAS_BEEN_TAKEN:
                                try {
                                    returnToPreviewMode(session, builder, handler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    callback.onError(e);
                                }
                                break;
                            default:
                                break;
                        }
                    }
                },
                handler);

    }

    private void lockFocus(CameraCaptureSession session, CaptureRequest.Builder builder,
                           CameraCaptureSession.CaptureCallback callback, Handler handler)
            throws CameraAccessException {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        session.stopRepeating();
        session.abortCaptures();
        session.capture(
                builder.build(),
                callback,
                handler
        );
        mCurrentStage = STAGE_LOCKING_FOCUS;
    }

    private void startPreCapture(CameraCaptureSession session, CaptureRequest.Builder builder,
                                 CameraCaptureSession.CaptureCallback captureCallback, Handler handler)
            throws CameraAccessException {
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        session.capture(
                builder.build(),
                captureCallback,
                handler
        );
        mCurrentStage = STAGE_PRECAPTURING_HAS_BEEN_STARTED;
    }

    private void returnToPreviewMode(CameraCaptureSession session, CaptureRequest.Builder builder, Handler handler)
            throws CameraAccessException {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        session.stopRepeating();
        session.abortCaptures();
        session.setRepeatingRequest(
                builder.build(),
                null,
                handler
        );
        mCurrentStage = STAGE_PREVIEWING;
    }

    private void captureStillPic(CameraCaptureSession session, ImageReader imageReader,
                                 CameraCaptureSession.CaptureCallback captureCallback, Handler handler)
            throws CameraAccessException {
        CameraDevice device = session.getDevice();
        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        builder.addTarget(imageReader.getSurface());
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        session.stopRepeating();
        session.abortCaptures();
        session.capture(
                builder.build(),
                captureCallback,
                handler
        );
    }

    /**
     * https://stackoverflow.com/questions/48406497/camera2-understanding-the-sensor-and-device-orientations
     * @param rotation
     * @return
     */
    private int getJpegOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.

        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }


    /**
     * The logic behind taking a pic is as followed:
     * Session 1: lock focus--auto exposure not converged--precapture--non precapture--take pic.
     * or Session 2: lock focus--auto exposure converged--take pic
     *
     * @param partialResult
     * @return
     */
    private String whatStageNow(@Nullable CaptureResult partialResult) {
        showLog("whatStageNow begin state: "+mCurrentStage);
        if(partialResult==null){
            return mCurrentStage;
        }
        Integer afState = partialResult.get(CaptureResult.CONTROL_AF_STATE);
        Integer aeState = partialResult.get(CaptureResult.CONTROL_AE_STATE);
        if (mCurrentStage.equals(STAGE_PRECAPTURING_HAS_BEEN_STARTED)) {
            if (aeState == null
                    || aeState == CONTROL_AE_STATE_PRECAPTURE
                    || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                mCurrentStage = STAGE_WAITING_FOR_NON_PRECAPTURE_STATE;
            }
        } else if (mCurrentStage.equals(STAGE_WAITING_FOR_NON_PRECAPTURE_STATE)) {
            if (aeState == null || aeState != CONTROL_AE_STATE_PRECAPTURE) {
                mCurrentStage = STAGE_READY_TO_TAKE_PICTURE;
            }
        } else if (mCurrentStage.equals(STAGE_LOCKING_FOCUS)) {
            if (afState == null) {
                mCurrentStage = STAGE_READY_TO_TAKE_PICTURE;
            } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                    || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    mCurrentStage = STAGE_READY_TO_TAKE_PICTURE;
                } else {
                    mCurrentStage = STAGE_YOU_SHOULD_START_PRECAPTURING;
                }
            }
        }
        showLog("whatStageNow end stage:"+mCurrentStage);
        return mCurrentStage;
    }

    private void showLog(String msg){
        Log.e(getClass().getSimpleName(),msg);
    }
}
