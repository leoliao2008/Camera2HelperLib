package tgi.com.librarycameratwo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public class CameraPresenter {
    private CameraView mView;
    private CameraModel mModel;
    private CameraManager mCameraManager;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Semaphore mLock = new Semaphore(1);//this is a thread safe lock to guarantee open camera/close camera are not executed simultaneously
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mRequestBuilder;
    private ImageReader mStillPictureReader;
    private OrientationEventListener mOrientationEventListener;
    private Integer mSensorOrientation;
    private Size mOptimalSize;
    private ImageReader mDynamicPictureReader;


    CameraPresenter(CameraView view) {
        mView = view;
        mCameraManager = (CameraManager) view.getContext().getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        mModel = new CameraModel(mCameraManager);
        mHandlerThread = new HandlerThread("CameraViewHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }


    void onAttachedToWindow() {
        if (!mView.isAvailable()) {
            setSurfaceTextureListener();
        } else {
            openCamera(mView.getSurfaceTexture());
        }
    }

    void onDetachedFromWindow() {
        if (mOrientationEventListener != null) {
            mOrientationEventListener.disable();
            mOrientationEventListener = null;
        }
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

    private void openCamera(final SurfaceTexture surface) {
        try {
            boolean acquire = mLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if (!acquire) {
                mView.handleError(new Exception("Camera is not available. Operation abort."));
                return;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            mView.handleError(e);
        }
        try {
            mModel.openCamera(
                    mModel.getRearCamera(),
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            try {
                                mLock.release();
                                mCamera = camera;
                                mSensorOrientation = mModel.getCameraSensorOrientation(camera);
                                showLog("sensor orientation is: " + mSensorOrientation);

                                mOptimalSize = mModel.chooseOptimalSize(
                                        mModel.getCameraCharacteristics(mCamera),
                                        CameraViewConstant.DESIRED_PREVIEW_SIZE.getWidth(),
                                        CameraViewConstant.DESIRED_PREVIEW_SIZE.getHeight());


                                // We fit the aspect ratio of TextureView to the size of preview we picked.
                                int rotation = mView.getDisplay().getRotation();
                                final Matrix matrix = mModel.getTransformMatrix(
                                        rotation,
                                        mView.getWidth(),
                                        mView.getHeight(),
                                        mOptimalSize
                                );
                                mView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mView.setTransform(matrix);
                                    }
                                });

                                if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
                                    mView.resize(mOptimalSize.getWidth(), mOptimalSize.getHeight());
                                } else {
                                    mView.resize(mOptimalSize.getHeight(), mOptimalSize.getWidth());
                                }
                                surface.setDefaultBufferSize(mOptimalSize.getWidth(), mOptimalSize.getHeight());

                                mStillPictureReader = ImageReader.newInstance(
                                        mOptimalSize.getWidth(),
                                        mOptimalSize.getHeight(),
                                        ImageFormat.JPEG,
                                        2
                                );


                                mDynamicPictureReader = ImageReader.newInstance(
                                        mOptimalSize.getWidth(),
                                        mOptimalSize.getHeight(),
                                        ImageFormat.YUV_420_888,
                                        2);
                                mDynamicPictureReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                                    @Override
                                    public void onImageAvailable(ImageReader imageReader) {
                                        showLog("mDynamicPictureReader got image!!!!");
                                        imageReader.acquireLatestImage().close();
                                    }
                                }, mHandler);

                                Surface preViewSurface = new Surface(surface);

                                mModel.startPreview(
                                        mCamera,
                                        preViewSurface,
                                        mStillPictureReader.getSurface(),
                                        mDynamicPictureReader.getSurface(),
                                        new PreviewSessionCallback() {
                                            @Override
                                            public void onSessionEstablished(CaptureRequest.Builder builder, CameraCaptureSession session) {
                                                showLog("onSessionEstablished");
                                                mRequestBuilder = builder;
                                                mSession = session;
                                            }

                                            @Override
                                            public void onFailToEstablishSession() {
                                                showLog("onFailToEstablishSession");
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
                            mLock.release();
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
        showLog("closeCamera");
        try {
            boolean acquire = mLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
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
                if(mDynamicPictureReader!=null){
                    mDynamicPictureReader.close();
                    mDynamicPictureReader=null;
                }
                mHandlerThread.quitSafely();
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

    void enableDynamicImageProcessing(final CameraView.DynamicImageCaptureCallback callback){
        if(mDynamicPictureReader!=null){
            mDynamicPictureReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Bitmap image = mModel.getImageFromImageReader(imageReader);
                    callback.onGetDynamicImage(image);
                }
            }, mHandler);
        }
    }

    void disableDynamicImageProcessing(){
        if(mDynamicPictureReader!=null){
            mDynamicPictureReader.setOnImageAvailableListener(null, null);
        }
    }

    private void showLog(String msg) {
        Log.e(getClass().getSimpleName(), msg);
    }




}
