package tgi.com.librarycameraview;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
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
    private CameraView mView;
    private android.os.Handler mBgThreadHandler;
    private HandlerThread mBgThread;
    private CameraViewModel mModel;
    private CameraManager mCameraManager;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Semaphore mCameraLock = new Semaphore(1);
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private int mTargetCameraWidth;
    private int mTargetCameraHeight;
    private ImageReader mTakeStillPicImageReader;

    CameraViewPresenter(CameraView view) {
        mView = view;
        mModel = new CameraViewModel();
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
        } else {
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
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
        }

    }

    private void initBgHandler() {
        mBgThread = new HandlerThread("CameraViewBgThread", Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mBgThreadHandler = new android.os.Handler(mBgThread.getLooper());
    }

    private void initAndOpenCamera(final SurfaceTexture surface, int width, int height) {
        //这是保证打开摄像头的操作和关闭摄像头的操作的不会同时进行，造成冲突。
        boolean isAcquire = false;
        try {
            isAcquire = mCameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!isAcquire) {
            showLog("isAcquire = false, abort", 0);
            return;
        }

        initBgHandler();

        try {
            final int deviceRotation = mView.getDisplay().getRotation();
            //判断当前视图是横屏还是竖屏，因为摄像头只有横屏，需要匹配最接近的一个相片尺寸。
            final boolean isSwapWidthAndHeight = (deviceRotation == Surface.ROTATION_0 || deviceRotation == Surface.ROTATION_180);
            if (isSwapWidthAndHeight) {
                mTargetCameraWidth = height;
                mTargetCameraHeight = width;
            } else {
                mTargetCameraWidth = width;
                mTargetCameraHeight = height;
            }
            final Size optimalSupportedSize = mModel.getOptimalSupportedSize(
                    mCameraManager,
                    mCameraId,
                    mTargetCameraWidth,
                    mTargetCameraHeight);
            //以下所有操作都需要在回调中执行，保证预览视图已经调整完成，尺寸是我想要的尺寸。
            mView.setSizeChangeListener(new CameraView.SizeChangeListener() {
                @Override
                public void onSizeChanged(int w, int h) {
                    //每次调整尺寸后调用这个回调一次即可，防止重复打开摄像头。
                    mView.setSizeChangeListener(null);
                    if (isSwapWidthAndHeight) {
                        //避免图片拉伸
                        mView.getSurfaceTexture().setDefaultBufferSize(h, w);
                        //更新最新尺寸
                        mTargetCameraWidth = h;
                        mTargetCameraHeight = w;
                    } else {
                        //避免图片拉伸
                        mView.getSurfaceTexture().setDefaultBufferSize(w, h);
                        //更新最新尺寸
                        mTargetCameraWidth = w;
                        mTargetCameraHeight = h;
                    }
                    Matrix matrix = mModel.genPreviewTransformMatrix(
                            optimalSupportedSize,
                            new Size(mTargetCameraWidth, mTargetCameraHeight),
                            deviceRotation);
                    mView.setTransform(matrix);

                    mTakeStillPicImageReader = ImageReader.newInstance(
                            mTargetCameraWidth,//尺寸和视图尺寸一致，保证预览和照片图片是一致的。
                            mTargetCameraHeight,
                            ImageFormat.JPEG,
                            2
                    );
                    mTakeStillPicImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            Image image = reader.acquireLatestImage();
                            image.close();
                        }
                    }, mBgThreadHandler);

                    try {
                        //预览尺寸和视图尺寸已经选好，调整好，不会拉伸变形后，正式打开摄像头。
                        mModel.openCamera(mCameraManager, mCameraId, new CameraDevice.StateCallback() {
                                    @Override
                                    public void onOpened(@NonNull CameraDevice camera) {
                                        mCameraLock.release();
                                        mCameraDevice = camera;
                                        try {
                                            mModel.createPreviewSession(
                                                    camera,
                                                    new CameraCaptureSessionStateCallback() {
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
                                                    },
                                                    mBgThreadHandler,
                                                    new Surface(surface),
                                                    mTakeStillPicImageReader.getSurface());
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
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        mView.onError(e);
                    }
                }
            });
            //这里让预览图根据最优预览尺寸调整大小比例，因为是异步操作，后续获取最新尺寸的操作必须在上面回调中执行。
            if (isSwapWidthAndHeight) {
                mView.resetWidthHeightRatio(optimalSupportedSize.getHeight(), optimalSupportedSize.getWidth());
            } else {
                mView.resetWidthHeightRatio(optimalSupportedSize.getWidth(), optimalSupportedSize.getHeight());
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
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (mCaptureSession != null) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (mBgThread != null && mBgThread.isAlive()) {
                    mBgThread.quitSafely();
                    mBgThread.join();
                    mBgThread = null;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mCameraLock.release();
        }
    }

    void showLog(String msg, int... logCodes) {
        LogUtil.showLog(getClass().getSimpleName(), msg, logCodes);
    }
}
