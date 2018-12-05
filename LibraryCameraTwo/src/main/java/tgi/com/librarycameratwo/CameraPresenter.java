package tgi.com.librarycameratwo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static tgi.com.librarycameratwo.CameraViewConstant.MAX_PREVIEW_HEIGHT;
import static tgi.com.librarycameratwo.CameraViewConstant.MAX_PREVIEW_WIDTH;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public class CameraPresenter {
    private CameraView mView;
    private CameraModel mModel;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Semaphore mCameraSwitchLock = new Semaphore(1);//this is a thread safe lock to guarantee open camera/close camera are not executed simultaneously
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mRequestBuilder;
    private ImageReader mStillPictureReader;
    private Integer mSensorOrientation;
    //    private Size mTensorFlowOptimalSize;
    private ImageReader mDynamicPictureReader;
    private volatile Semaphore mDynamicImageReaderLock = new Semaphore(1);
    private CameraView.DynamicImageCaptureCallback mDynamicImageCaptureCallback;
    private Size mOptimalPreviewSize;


    CameraPresenter(CameraView view) {
        mView = view;
        CameraManager cameraManager = (CameraManager) view.getContext().getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        mModel = new CameraModel(cameraManager);

    }


    void onAttachedToWindow() {
        if (!mView.isAvailable()) {
            setSurfaceTextureListener();
        } else {
            openCamera(mView.getSurfaceTexture());
        }
    }

    void onDetachedFromWindow() {
        closeCamera();
    }


    private void setSurfaceTextureListener() {
        mView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, final int height) {
                openCamera(mView.getSurfaceTexture());
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configurePreviewTransformation(new Size(width, height), mOptimalPreviewSize);

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    private void configurePreviewTransformation(Size formerSize, Size targetSize) {
        int rotation = mView.getDisplay().getRotation();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            mView.resize(targetSize.getWidth(), targetSize.getHeight());
        } else {
            mView.resize(targetSize.getHeight(), targetSize.getWidth());
        }

        final Matrix matrix = mModel.getPreviewTransformMatrix(
                rotation,
                formerSize,
                targetSize
        );
        mView.post(new Runnable() {
            @Override
            public void run() {
                mView.setTransform(matrix);
            }
        });
    }

    private void openCamera(final SurfaceTexture surface) {
        try {
            boolean acquire = mCameraSwitchLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if (!acquire) {
                mView.handleError(new Exception("Camera is not available. Operation abort."));
                return;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            mView.handleError(e);
        }

        mHandlerThread = new HandlerThread("CameraViewHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        try {
            mModel.openCamera(
                    mModel.getRearCamera(),
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            try {
                                mCameraSwitchLock.release();
                                mCamera = camera;
                                mSensorOrientation = mModel.getCameraSensorOrientation(camera);

                                final Size tensorFlowOptimalSize = mModel.chooseTensorFlowOptimalSize(
                                        mModel.getCameraCharacteristics(mCamera),
                                        CameraViewConstant.DESIRED_TENSOR_FLOW_PREVIEW_SIZE.getWidth(),
                                        CameraViewConstant.DESIRED_TENSOR_FLOW_PREVIEW_SIZE.getHeight());

                                mModel.initTensorFlowInput(tensorFlowOptimalSize);

                                // We fit the aspect ratio of TextureView to the size of preview we picked.
                                int formerWidth = mView.getWidth();
                                int formerHeight = mView.getHeight();

                                Point point = new Point();
                                mView.getDisplay().getSize(point);
                                int maxPreviewWidth = point.x;
                                int maxPreviewHeight = point.y;
                                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                                }

                                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                                }
                                StreamConfigurationMap map = mModel.getCameraCharacteristics(mCamera).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                                Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
                                mOptimalPreviewSize = mModel.choosePreviewOptimalSize(
                                        outputSizes,
                                        formerWidth,
                                        formerHeight,
                                        maxPreviewWidth,
                                        maxPreviewHeight,
                                        Collections.max(
                                                Arrays.asList(outputSizes),
                                                new Comparator<Size>() {
                                                    @Override
                                                    public int compare(Size first, Size second) {
                                                        return first.getHeight() * first.getWidth() - second.getWidth() * second.getHeight();
                                                    }
                                                }
                                        )

                                );


                                configurePreviewTransformation(new Size(formerWidth, formerHeight), mOptimalPreviewSize);

                                mStillPictureReader = ImageReader.newInstance(
                                        mOptimalPreviewSize.getWidth(),
                                        mOptimalPreviewSize.getHeight(),
                                        ImageFormat.JPEG,
                                        2
                                );

                                mDynamicPictureReader = ImageReader.newInstance(
                                        tensorFlowOptimalSize.getWidth(),
                                        tensorFlowOptimalSize.getHeight(),
                                        ImageFormat.YUV_420_888,
                                        2);
                                mDynamicPictureReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                                    @Override
                                    public void onImageAvailable(final ImageReader imageReader) {
                                        final Image image = imageReader.acquireLatestImage();
                                        if (image == null) {
                                            return;
                                        }
                                        if (mDynamicImageCaptureCallback != null && mDynamicImageReaderLock.tryAcquire()) {
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Bitmap bitmap = mModel.getImageFromYUV_420_888Format(image, tensorFlowOptimalSize);
                                                    if (mDynamicImageCaptureCallback != null) {
                                                        mDynamicImageCaptureCallback.onGetDynamicImage(bitmap);
                                                    }
                                                    mDynamicImageReaderLock.release();
                                                }
                                            }).start();
                                        } else {
                                            image.close();
                                        }
                                    }
                                }, mHandler);


                                mModel.startPreview(
                                        mCamera,
                                        new Surface(surface),
                                        mStillPictureReader.getSurface(),
                                        mDynamicPictureReader.getSurface(),
                                        new PreviewSessionCallback() {
                                            @Override
                                            public void onSessionEstablished(CaptureRequest.Builder builder, CameraCaptureSession session) {
                                                mRequestBuilder = builder;
                                                mSession = session;
                                            }

                                            @Override
                                            public void onFailToEstablishSession() {
                                                closeCamera();
                                            }
                                        }, mHandler);

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                                closeCamera();
                                mView.handleError(e);
                            }
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            closeCamera();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            closeCamera();
                            mView.handleError(new Exception("Critical Error when trying to " +
                                    "open camera: " + camera.getId() + " error code: " + error));
                        }

                        @Override
                        public void onClosed(@NonNull CameraDevice camera) {
                            super.onClosed(camera);
                            mCameraSwitchLock.release();
                        }
                    },
                    mHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mView.handleError(e);
        }
    }


    private void closeCamera() {
        try {
            boolean acquire = mCameraSwitchLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if (acquire) {
                mRequestBuilder = null;
                if (mSession != null) {
                    mSession.close();
                    mSession = null;
                }
                if (mCamera != null) {
                    mCamera.close();
                    mCamera = null;
                }
                if (mStillPictureReader != null) {
                    mStillPictureReader.close();
                    mStillPictureReader = null;
                }
                if (mDynamicPictureReader != null) {
                    mDynamicPictureReader.close();
                    mDynamicPictureReader = null;
                }
                mHandlerThread.quitSafely();
                mHandlerThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            mView.handleError(e);
        }
    }

    void takePic(TakePicCallback callback) {
        if (mRequestBuilder == null || mSession == null) {
            return;
        }
        mModel.takePic(
                mRequestBuilder,
                mSession,
                mStillPictureReader,
                mView.getDisplay().getRotation(),
                mSensorOrientation,
                callback,
                mHandler);
    }


    void enableDynamicImageProcessing(CameraView.DynamicImageCaptureCallback callback) {
        mDynamicImageCaptureCallback = callback;
    }

    void disableDynamicImageProcessing() {
        mDynamicImageCaptureCallback = null;
    }

    private void showLog(String msg) {
        if (CameraViewConstant.IS_DEBUG_MODE) {
            Log.e(getClass().getSimpleName(), msg);
        }
    }


}
