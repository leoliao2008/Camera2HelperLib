package tgi.com.librarycameraview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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
    private Semaphore mTakeStillPicLock = new Semaphore(1);
    private Semaphore mDynamicProcessingLock = new Semaphore(1);
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private volatile int mTargetCameraWidth;
    private volatile int mTargetCameraHeight;
    private ImageReader mTakeStillPicImageReader;
    private ImageReader mTensorFlowImageReader;
    private static final int CAPTURE_STATE_WAITING_LOCK = 1;
    private static final int CAPTURE_STATE_FOCUSED = 2;
    private static final int CAPTURE_STATE_PIC_TAKEN = 3;
    private static final int CAPTURE_STATE_PREVIEWING = 4;
    private AtomicInteger mCurrentCaptureState = new AtomicInteger(CAPTURE_STATE_PREVIEWING);
    private Semaphore mProcessLock = new Semaphore(1);
    private TensorFlowImageSubscriber mTensorFlowImageSubscriber;
    private boolean mIsSwapWidthAndHeight;
    private Size mSupportedPreviewSize;
    private volatile boolean mIsCameraRunning = false;


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
        setSurfaceTextureListener();
    }

    private void setSurfaceTextureListener() {
        mView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                initAndOpenCamera(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                //随着SurfaceTexture尺寸的变化，同步预览图的矩阵，避免发生图像拉伸变形。
                //这里非常重要，实测中发现如果不在这里同步，预览图经常会扭曲变形。
                int deviceRotation = mView.getDisplay().getRotation();
                configurePreviewTransform(width, height, surface, deviceRotation);
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

    void openCamera() {
        if (mView.isAvailable()) {
            initAndOpenCamera(mView.getSurfaceTexture(), mView.getWidth(), mView.getHeight());
        } else {
            setSurfaceTextureListener();
        }
    }

    private void initBgHandler() {
        mBgThread = new HandlerThread("CameraViewBgThread", Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mBgThreadHandler = new android.os.Handler(mBgThread.getLooper());
    }

    void initAndOpenCamera(final SurfaceTexture surface, int width, int height) {
        //这是保证打开摄像头的操作和关闭摄像头的操作的不会同时进行，造成冲突。
        if (!mCameraLock.tryAcquire()) {
            return;
        }
        //如果摄像头已经在运行，直接返回。
        if (mIsCameraRunning) {
            return;
        }

        initBgHandler();

        try {
            final int deviceRotation = mView.getDisplay().getRotation();
            //判断当前视图是横屏还是竖屏，因为摄像头只有横屏，需要匹配最接近的一个相片尺寸。
            mIsSwapWidthAndHeight = (deviceRotation == Surface.ROTATION_0 || deviceRotation == Surface.ROTATION_180);
            if (mIsSwapWidthAndHeight) {
                mTargetCameraWidth = height;
                mTargetCameraHeight = width;
            } else {
                mTargetCameraWidth = width;
                mTargetCameraHeight = height;
            }

            //tensorFlow
            final Size tensorFlowOptimalSize = mModel.getTensorFlowOptimalSize(
                    mCameraManager.getCameraCharacteristics(mCameraId),
                    CONSTANTS.DESIRED_TENSOR_FLOW_PREVIEW_SIZE.getWidth(),
                    CONSTANTS.DESIRED_TENSOR_FLOW_PREVIEW_SIZE.getHeight());

            mModel.initTensorFlowInput(tensorFlowOptimalSize, deviceRotation);

            mSupportedPreviewSize = mModel.getOptimalSupportedPreviewSize(
                    mCameraManager,
                    mCameraId,
                    mTargetCameraWidth,
                    mTargetCameraHeight);

            //初步计算预览图的矩阵，避免拉伸。只是在这里设置是不够的，因为待会还会调整
            //surface texture的尺寸，到时还要重新计算。计算的时机为onSurfaceTextureSizeChanged这个回调中。
            //为什么知道待会要重新计算矩阵，在这里提前设置一次？这是因为onSurfaceTextureSizeChanged
            //在尺寸没有变更时不会触发。这里是为了保险起见。
            configurePreviewTransform(width, height, surface, deviceRotation);

            mTakeStillPicImageReader = ImageReader.newInstance(
                    mSupportedPreviewSize.getWidth(),
                    mSupportedPreviewSize.getHeight(),
                    ImageFormat.JPEG,
                    2
            );

            //tensorFlow
            mTensorFlowImageReader = ImageReader.newInstance(
                    tensorFlowOptimalSize.getWidth(),
                    tensorFlowOptimalSize.getHeight(),
                    ImageFormat.YUV_420_888,
                    2
            );
            mTensorFlowImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(final ImageReader reader) {
                    boolean isForfeit = true;
                    if (mTensorFlowImageSubscriber != null) {
                        if (mDynamicProcessingLock.tryAcquire()) {
                            isForfeit = false;
                            //新建一条线程专门处理以下逻辑，bgThread处理的任务有点多了，否则预览页面会卡顿
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    Image image = reader.acquireLatestImage();
                                    // image将在getImageFromYUV_420_888Format运行到一半的时候在函数内部被释放。
                                    // 因为运算太耗时，如果放在函数外面释放，有可能在释放的时候跳空指针。
                                    Bitmap bitmap = mModel.getImageFromYUV_420_888Format(image, tensorFlowOptimalSize);
                                    //这里需要再次判断一次非空，因为前面消耗了一定时间
                                    if (bitmap != null && mTensorFlowImageSubscriber != null) {
                                        mTensorFlowImageSubscriber.onGetDynamicImage(bitmap);
                                    }
                                    mDynamicProcessingLock.release();
                                }
                            }).start();
                        }
                    }
                    if (isForfeit) {
                        //手动处理image，否则接收不到新的图像。
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            image.close();
                        }
                    }
                }
            }, mBgThreadHandler);

            try {
                //正式打开摄像头。
                mModel.openCamera(mCameraManager, mCameraId, new CameraDevice.StateCallback() {
                            @Override
                            public void onOpened(@NonNull CameraDevice camera) {
                                mCameraDevice = camera;
                                showLog("camera open!", 1);
                                try {
                                    mModel.createPreviewSession(
                                            camera,
                                            new CameraCaptureSessionStateCallback() {
                                                @Override
                                                public void onSessionEstablished(CaptureRequest.Builder builder,
                                                                                 CameraCaptureSession session) {
                                                    mRequestBuilder = builder;
                                                    mCaptureSession = session;
                                                    mIsCameraRunning = true;
                                                    mCameraLock.release();
                                                }

                                                @Override
                                                public void onFailToEstablishSession() {
                                                    closeCamera();
                                                    mCameraLock.release();
                                                }
                                            },
                                            mBgThreadHandler,
                                            new Surface(surface),
                                            mTakeStillPicImageReader.getSurface(),
                                            mTensorFlowImageReader.getSurface());
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    mView.onError(e);
                                }

                            }

                            @Override
                            public void onDisconnected(@NonNull CameraDevice camera) {
                                mCameraLock.release();
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
            //这里让预览图根据最优预览尺寸调整大小比例，因为预览图尺寸发生了变化，需要在onSurfaceTextureSizeChanged回调中调整预览图的矩阵。
            if (mIsSwapWidthAndHeight) {
                mView.resetWidthHeightRatio(mSupportedPreviewSize.getHeight(), mSupportedPreviewSize.getWidth());
            } else {
                mView.resetWidthHeightRatio(mSupportedPreviewSize.getWidth(), mSupportedPreviewSize.getHeight());
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mView.onError(e);
            closeCamera();
        }
    }

    private void configurePreviewTransform(int w, int h, SurfaceTexture surface, int deviceRotation) {
        if (mIsSwapWidthAndHeight) {
            //更新最新尺寸
            mTargetCameraWidth = h;
            mTargetCameraHeight = w;
        } else {
            //更新最新尺寸
            mTargetCameraWidth = w;
            mTargetCameraHeight = h;
        }
        //防止预览图变形
        surface.setDefaultBufferSize(mSupportedPreviewSize.getWidth(), mSupportedPreviewSize.getHeight());

        //防止预览图变形
        Matrix matrix = mModel.genPreviewTransformMatrix(
                mSupportedPreviewSize,
                new Size(mTargetCameraWidth, mTargetCameraHeight),
                deviceRotation);
        mView.setTransform(matrix);
    }

    void takeStillPic(final TakeStillPicCallback callback) {
        //这几个对象必须能用
        if (mCaptureSession == null || mRequestBuilder == null || mCameraDevice == null) {
            callback.onFailToGetPic();
            return;
        }
        //先设置好得到照片后的回调
        mTakeStillPicImageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        if (mTakeStillPicLock.tryAcquire()) {//保证同一时间只能处理一张照片
                            Image image = reader.acquireLatestImage();
                            if (image != null) {
                                //获取到图片了，撤销回调。
                                mTakeStillPicImageReader.setOnImageAvailableListener(null, null);
                                //第四步 拍照完成
                                showLog("第四步 拍照完成", 0);
                                //在这里更新当前拍照状态，比onCaptureCompleted更可靠
                                mCurrentCaptureState.compareAndSet(CAPTURE_STATE_FOCUSED, CAPTURE_STATE_PIC_TAKEN);

                                //第五步 拍照完成，返回预览
                                if (mCurrentCaptureState.compareAndSet(CAPTURE_STATE_PIC_TAKEN, CAPTURE_STATE_PREVIEWING)) {
                                    showLog("第五步 拍照完成，返回预览", 0);
                                    try {
                                        mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                                        mCaptureSession.setRepeatingRequest(
                                                mRequestBuilder.build(),
                                                null,
                                                mBgThreadHandler
                                        );

                                    } catch (CameraAccessException e) {
                                        e.printStackTrace();
                                    }
                                }

                                Bitmap src = mModel.getBitmapFromJpegFormat(image);
                                image.close();

                                //计算当前手机的旋转角度：90,180,270,360（0）四种角度
                                int rotation = mView.getDisplay().getRotation() * 90;
                                //这里选择360而不是0是因为方便后面计算
                                if (rotation == 0) {
                                    rotation = 360;
                                }
                                Bitmap dest = Bitmap.createBitmap(mView.getWidth(), mView.getHeight(), src.getConfig());
                                Canvas canvas = new Canvas(dest);
                                Matrix matrix = new Matrix();
                                RectF rectFrom = new RectF(0, 0, src.getWidth(), src.getHeight());
                                RectF rectTo = new RectF(0, 0, dest.getWidth(), dest.getHeight());
                                matrix.postTranslate(rectTo.centerX() - rectFrom.centerX(), rectTo.centerY() - rectFrom.centerY());
                                //经真机上实测，需要旋转的角度（补充旋转角度）=450-当前手机旋转角度.
                                matrix.postRotate(450 - rotation, dest.getWidth() / 2, dest.getHeight() / 2);
                                float scaleX;
                                float scaleY;
                                //根据真机测试得来的逻辑
                                if (rotation == 90 || rotation == 270) {
                                    scaleX = dest.getWidth() * 1.0f / src.getWidth();
                                    scaleY = dest.getHeight() * 1.0f / src.getHeight();
                                } else {
                                    scaleX = dest.getWidth() * 1.0f / src.getHeight();
                                    scaleY = dest.getHeight() * 1.0f / src.getWidth();
                                }
                                matrix.postScale(scaleX, scaleY, rectTo.centerX(), rectTo.centerY());
                                canvas.drawBitmap(src, matrix, new Paint());
                                //在横屏时，最终照片视角稍微比预览的要大，待解决。
                                callback.onGetStillPic(dest);
                            } else {
                                callback.onFailToGetPic();
                            }
                            mTakeStillPicLock.release();
                        }
                    }
                },
                mBgThreadHandler);
        //开始拍照
        //第一步：lock focus（聚焦）
        showLog("第一步：lock focus（聚焦）", 0);
        if (mCurrentCaptureState.compareAndSet(CAPTURE_STATE_PREVIEWING, CAPTURE_STATE_WAITING_LOCK)) {
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            try {
                mCaptureSession.setRepeatingRequest(
                        mRequestBuilder.build(),
                        new CameraCaptureSession.CaptureCallback() {
                            private void process(CameraCaptureSession session, CaptureRequest request, CaptureResult result) {
                                //上锁，保证处理完一个事件再处理另一个事件，否则跳过
                                if (mProcessLock.tryAcquire()) {
                                    switch (mCurrentCaptureState.get()) {
                                        case CAPTURE_STATE_PREVIEWING:
                                            break;
                                        case CAPTURE_STATE_WAITING_LOCK:
                                            //第二步 检查是否聚焦成功：
                                            showLog("第二步 检查是否聚焦成功", 0);
                                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                                            if (afState == null
                                                    || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                                                    || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                                //第三步 如果成功，开始静态拍照
                                                showLog("第三步 聚焦成功，开始静态拍照", 0);
                                                if (mCurrentCaptureState.compareAndSet(CAPTURE_STATE_WAITING_LOCK, CAPTURE_STATE_FOCUSED)) {
                                                    try {
                                                        CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                                        builder.addTarget(mTakeStillPicImageReader.getSurface());
                                                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                                        mCaptureSession.stopRepeating();
                                                        mCaptureSession.abortCaptures();
                                                        mCaptureSession.capture(
                                                                builder.build(),
                                                                this,
                                                                mBgThreadHandler
                                                        );
                                                    } catch (CameraAccessException e) {
                                                        e.printStackTrace();
                                                        callback.onFailToGetPic();
                                                    }
                                                }
                                            } else {
                                                showLog("第三步 聚焦失败，返回第二步", 0);
                                            }
                                            break;
                                    }
                                    mProcessLock.release();
                                }
                            }

                            @Override
                            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                                super.onCaptureProgressed(session, request, partialResult);
                                process(session, request, partialResult);
                            }

                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                //经测试，在乐视手机上拍照的时候，在获得照片内容前会先执行这个回调。因此这个回调不代表真的拍照成功了。
                                process(session, request, result);
                            }
                        },
                        mBgThreadHandler
                );
            } catch (CameraAccessException e) {
                e.printStackTrace();
                callback.onFailToGetPic();
            }
        }


    }

    void registerTensorFlowImageSubscriber(TensorFlowImageSubscriber listener) {
        mTensorFlowImageSubscriber = listener;
    }

    void unRegisterTensorFlowImageSubscriber() {
        mTensorFlowImageSubscriber = null;
    }

    void closeCamera() {
        if (mCameraLock.tryAcquire()) {

            if (mCaptureSession != null) {
                try {
                    mCaptureSession.stopRepeating();
                    mCaptureSession.abortCaptures();
                    mCaptureSession.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mCaptureSession = null;
                }
            }
            if (mBgThread != null && mBgThread.isAlive()) {
                mBgThread.quitSafely();
                try {
                    mBgThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mBgThread = null;
            }
            //摄像头要最后关掉，否则前面几个会报错。
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (mTakeStillPicImageReader != null) {
                mTakeStillPicImageReader.close();
                mTakeStillPicImageReader = null;
            }

            if (mTensorFlowImageReader != null) {
                mTensorFlowImageReader.close();
                mTensorFlowImageReader = null;
            }

            mIsCameraRunning = false;

            mCameraLock.release();
        }
    }

    void showLog(String msg, int... logCodes) {
        if (logCodes.length < 1) {
            LogUtil.showLog(msg, 1);
        } else {
            LogUtil.showLog(msg, logCodes);
        }

    }
}
