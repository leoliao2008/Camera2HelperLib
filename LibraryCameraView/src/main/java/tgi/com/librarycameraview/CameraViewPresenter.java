package tgi.com.librarycameraview;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 5/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>AndroidCameraDemo</i>
 * <p><b>Description:</b></p>
 */
class CameraViewPresenter {
    private CameraView2 mView;
    private android.os.Handler mBgThreadHandler;
    private HandlerThread mBgThread;
    private CameraViewModel mModel;
    private CameraManager mCameraManager;
    private SurfaceTexture mSurfaceTexture;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Semaphore mCameraLock = new Semaphore(1);
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mCaptureSession;

    CameraViewPresenter(CameraView2 view) {
        mView = view;
        mModel = new CameraViewModel();
        mBgThread = new HandlerThread("CameraViewBgThread",Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mBgThreadHandler = new android.os.Handler(mBgThread.getLooper());
        mCameraManager = (CameraManager) mView.getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameraId = mModel.getRearCameraId(mCameraManager);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mView.onError(e);
        }
    }

    void openCamera() {
        if (mView.isAvailable()) {
            initAndOpenCamera(mView.getSurfaceTexture(), mView.getWidth(), mView.getHeight());
        } else
            mView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    initAndOpenCamera(surface, width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    closeCamera();
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });

    }

    private void initAndOpenCamera(SurfaceTexture surface, int width, int height) {
        mSurfaceTexture = surface;
        try {
            int trueSensorOrientation = mModel.getTrueSensorOrientation(
                    mCameraManager,
                    mCameraId,
                    mView.getDisplay().getRotation());
            Size optimalSupportedSize = mModel.getOptimalSupportedSize(
                    mCameraManager,
                    mCameraId,
                    width,
                    height,
                    trueSensorOrientation);
            Matrix matrix = mModel.getPreviewTransformMatrix(
                    new Size(width, height),
                    optimalSupportedSize,
                    trueSensorOrientation);
            mView.setTransform(matrix);
            try {
                boolean isAcquire = mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
                if (!isAcquire) {
                    return;
                }
                mModel.openCamera(mCameraManager, mCameraId, new CameraDevice.StateCallback() {
                            @Override
                            public void onOpened(@NonNull CameraDevice camera) {
                                mCameraLock.release();
                                mCameraDevice = camera;
                                try {
                                    mModel.createPreviewSession(camera, new CameraCaptureSessionStateCallback() {
                                        @Override
                                        public void onConfigured(CaptureRequest.Builder builder,
                                                                 CameraCaptureSession session) {
                                            mRequestBuilder = builder;
                                            mCaptureSession = session;

                                        }

                                        @Override
                                        public void onConfigureFailed(CameraCaptureSession session) {
                                            closeCamera();

                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            closeCamera();
                                            mView.onError(e);
                                        }
                                    }, new Surface(mView.getSurfaceTexture()));
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    mView.onError(e);
                                }

                            }

                            @Override
                            public void onDisconnected(@NonNull CameraDevice camera) {
                                mCameraLock.release();
                                closeCamera();

                            }

                            @Override
                            public void onError(@NonNull CameraDevice camera, int error) {
                                mCameraLock.release();
                                closeCamera();

                            }
                        },
                        mBgThreadHandler);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        } catch (CameraAccessException e) {
            e.printStackTrace();
            mView.onError(e);
            closeCamera();
        }

    }

    void closeCamera() {
        if (mCameraLock.tryAcquire()) {
            try {
                if (mCaptureSession != null) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                mSurfaceTexture.release();
                mBgThread.quitSafely();
                mBgThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mCameraLock.release();
        }
    }
}
