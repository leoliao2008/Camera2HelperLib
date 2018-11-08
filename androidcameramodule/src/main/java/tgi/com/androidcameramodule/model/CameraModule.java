package tgi.com.androidcameramodule.model;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import tgi.com.androidcameramodule.callback.CameraCallback;
import tgi.com.androidcameramodule.fragment.CameraPreviewFragment;
import tgi.com.androidcameramodule.utils.ImageUtils;

/**
 * This model is with reference to https://blog.csdn.net/CrazyMo_/article/details/78182243
 */
public class CameraModule {
    private CameraManager mCameraManager;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private ImageReader mImageScanner;
    private boolean isComputing = false;
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 128;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private CameraCallback mCallback;
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mCaptureSession;

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
    /**
     * The current state of camera state for taking pictures.
     */
    private int mState = STATE_PREVIEW;
    private Handler mBgHandler;
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private PictureCaptureCallback mCaptureCallback;

    /**
     * Conversion from screen mRotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Integer mSensorOrientation;
    private ImageReader mPicImageReader;
    private CameraPreviewFragment.TakePicListener mTakePicListener;


    public CameraModule(CameraManager cameraManager,CameraCallback callback,Handler handler) {
        mCameraManager = cameraManager;
        mCallback=callback;
        mBgHandler=handler;
    }

    private void onPreviewSizeChosen(Size size, int screenRotation, Integer sensorOrientation) {
        int previewWidth = size.getWidth();
        int previewHeight = size.getHeight();
        sensorOrientation = screenRotation + sensorOrientation;
        rgbBytes = new int[previewWidth * previewHeight];
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth,
                        previewHeight,
                        INPUT_SIZE, INPUT_SIZE,
                        sensorOrientation,
                        true);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
        yuvBytes = new byte[3][];
    }

    private void processImage(ImageReader reader, int previewWidth, int previewHeight) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                showLog("processImage: image==null");
                return;
            }

            if (isComputing) {
                image.close();
                showLog("processImage: iscomputing... abort.");
                return;
            }
            isComputing = true;

            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes);
            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            showLog("processImage Exception: "+e.getMessage());
            return;
        }

        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        mCallback.onImageAvailable(croppedBitmap);
        isComputing = false;
    }

    private void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
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


    @SuppressLint("MissingPermission")
    public void openMainCamera(
            String cameraId,
            final Surface preview,
            final Size previewSize) throws CameraAccessException, IllegalArgumentException, SecurityException {

        mImageScanner = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
        mImageScanner.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(final ImageReader reader) {
                showLog("begin to process image...");
                processImage(reader, previewSize.getWidth(), previewSize.getHeight());
            }
        }, mBgHandler);

        mPicImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
        mPicImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if(mTakePicListener!=null){
                    Image image = reader.acquireLatestImage();
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes=new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    mTakePicListener.onPictureTaken(bitmap);
                    image.close();
                }
                mPicImageReader.close();
            }
        },mBgHandler);


        mCameraManager.openCamera(
                cameraId,
                new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull final CameraDevice camera) {
                        mCallback.onCameraOpen(camera);
                        try {
                            final List<Surface> outputs = new ArrayList<Surface>() {
                                {
                                    add(preview);
                                    add(mImageScanner.getSurface());
                                    add(mPicImageReader.getSurface());
                                }
                            };
                            mRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            mRequestBuilder.addTarget(preview);
                            camera.createCaptureSession(
                                    outputs,
                                    new CameraCaptureSession.StateCallback() {
                                        @Override
                                        public void onConfigured(@NonNull CameraCaptureSession session) {
                                            mCaptureSession=session;
                                            //auto focus
                                            mRequestBuilder.set(
                                                    CaptureRequest.CONTROL_AF_MODE,
                                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                            );
                                            //auto flash
                                            mRequestBuilder.set(
                                                    CaptureRequest.CONTROL_AE_MODE,
                                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                                            );
                                            try {
                                                session.setRepeatingRequest(
                                                        mRequestBuilder.build(),
                                                        null,
                                                        mBgHandler
                                                );
                                            } catch (CameraAccessException e) {
                                                e.printStackTrace();
                                            }

                                        }

                                        @Override
                                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                            camera.close();
                                            mCallback.onError("createCaptureSession->onConfigureFailed: " + session.toString());

                                        }
                                    },
                                    mBgHandler);
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
                        mCallback.onError("Camera Fails to open, error code: " + error);

                    }
                },
                mBgHandler
        );
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    public void takePic(CameraDevice cameraDevice, int screenRotation, CameraPreviewFragment.TakePicListener listener) {
        mTakePicListener=listener;
        try {
            mCaptureCallback=new PictureCaptureCallback(cameraDevice,screenRotation);
            // This is how to tell the camera to lock focus.
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mRequestBuilder.build(), mCaptureCallback,
                    mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    /**
//     * Run the precapture sequence for capturing a still image. This method should be called when
//     * we get a response in {@link #mCaptureCallback} from {@link #takePic(CameraDevice, int, CameraPreviewFragment.TakePicListener)}.
//     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mRequestBuilder.build(), mCaptureCallback,
                    mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

//    /**
//     * Capture a still picture. This method should be called when we get a response in
//     * {@link #mCaptureCallback} from both {@link #takePic(CameraDevice, int, CameraPreviewFragment.TakePicListener)}.
//     */
    private void captureStillPicture(CameraDevice camera,ImageReader imageReader,int rotation) {
        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Orientation
//            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen mRotation.
     *
     * @param rotation The screen mRotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mRequestBuilder.build(), mCaptureCallback,
                    mBgHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mRequestBuilder.build(), mCaptureCallback,
                    mBgHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public Matrix getTransformMatrix(int textureWidth, int textureHeight, int rotation, Size optimalPreviewSize) {
        Matrix matrix = new Matrix();
        Size previewSize = new Size(optimalPreviewSize.getWidth(), optimalPreviewSize.getHeight());
        RectF viewRect = new RectF(0, 0, textureWidth, textureHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) textureHeight / previewSize.getHeight(),
                    (float) textureWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
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

    public Size chooseOptimalPreviewSize(
            int textureWidth,
            int textureHeight,
            String cameraId,
            Point screenSize,
            int displayRotation) {
        try {
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            //noinspection ConstantConditions
            mSensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
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
            }
            int rotatedPreviewWidth = textureWidth;
            int rotatedPreviewHeight = textureHeight;
            int maxPreviewWidth = screenSize.x;
            int maxPreviewHeight = screenSize.y;

            if (swappedDimensions) {
                rotatedPreviewWidth = textureHeight;
                rotatedPreviewHeight = textureWidth;
                maxPreviewWidth = screenSize.y;
                maxPreviewHeight = screenSize.x;
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
            Size optimalSize = chooseOptimalPreviewSize(
                    rotatedPreviewWidth,
                    rotatedPreviewHeight,
                    maxPreviewWidth,
                    maxPreviewHeight,
                    cameraId
            );
            if (optimalSize != null) {
                onPreviewSizeChosen(optimalSize, displayRotation, mSensorOrientation);
                // We fit the aspect ratio of TextureView to the size of preview we picked.
                return optimalSize;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }



    private Size chooseOptimalPreviewSize(int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, String cameraId) {
        try {
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                // Collect the supported resolutions that are at least as big as the preview Surface
                Size[] choices = map.getOutputSizes(SurfaceTexture.class);
                Size largest = Collections.max(
                        Arrays.asList(choices),
                        new CompareSizesByArea());
                if (choices.length > 0) {
                    List<Size> bigEnough = new ArrayList<>();
                    // Collect the supported resolutions that are smaller than the preview Surface
                    int w = largest.getWidth();
                    int h = largest.getHeight();
                    List<Size> notBigEnough = new ArrayList<>();
                    for (Size option : choices) {
                        if (option.getWidth() <= maxWidth
                                && option.getHeight() <= maxHeight
                                &&option.getWidth()==option.getHeight()*w/h) {
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

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }


    private class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size first, Size second) {
            return first.getWidth() * first.getHeight() - second.getWidth() * second.getHeight();
        }
    }

    private void showLog(String msg) {
        Log.e(getClass().getSimpleName(), msg);
    }

    private class PictureCaptureCallback extends CameraCaptureSession.CaptureCallback {
        private CameraDevice mCameraDevice;
        private int mRotation;

        public PictureCaptureCallback(CameraDevice cameraDevice, int rotation) {
            mCameraDevice = cameraDevice;
            mRotation = rotation;
        }

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture(mCameraDevice,mPicImageReader,mRotation);
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture(mCameraDevice,mPicImageReader,mRotation);
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture(mCameraDevice,mPicImageReader,mRotation);
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }


    }

}
