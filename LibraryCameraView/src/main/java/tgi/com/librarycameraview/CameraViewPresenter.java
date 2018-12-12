package tgi.com.librarycameraview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.hardware.camera2.CaptureFailure;
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

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    private CaptureRequest.Builder mRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private volatile int mTargetCameraWidth;
    private volatile int mTargetCameraHeight;
    private ImageReader mTakeStillPicImageReader;
    private Semaphore mTakeStillPicLock = new Semaphore(1);
    private static final int CAPTURE_STATE_WAITING_LOCK = 1;
    private static final int CAPTURE_STATE_FOCUSED = 2;
    private static final int CAPTURE_STATE_PIC_TAKEN = 3;
    private static final int CAPTURE_STATE_PREVIEWING = 4;
    private AtomicInteger mCurrentCaptureState = new AtomicInteger(CAPTURE_STATE_PREVIEWING);
    private Semaphore mProcessLock = new Semaphore(1);

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
        if (!mCameraLock.tryAcquire()) {
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
                public void onSizeChanged(final int w, final int h) {
                    //每次调整尺寸后调用这个回调一次即可，防止重复打开摄像头。
                    mView.setSizeChangeListener(null);
                    if (isSwapWidthAndHeight) {
                        //更新最新尺寸
                        mTargetCameraWidth = h;
                        mTargetCameraHeight = w;
                    } else {
                        //更新最新尺寸
                        mTargetCameraWidth = w;
                        mTargetCameraHeight = h;
                    }
                    surface.setDefaultBufferSize(optimalSupportedSize.getWidth(), optimalSupportedSize.getHeight());

                    Matrix matrix = mModel.genPreviewTransformMatrix(
                            optimalSupportedSize,
                            new Size(mTargetCameraWidth, mTargetCameraHeight),
                            deviceRotation);
                    mView.setTransform(matrix);

                    mTakeStillPicImageReader = ImageReader.newInstance(
                            optimalSupportedSize.getWidth(),
                            optimalSupportedSize.getHeight(),
                            ImageFormat.JPEG,
                            2
                    );

                    try {
                        //保证预览尺寸已经调整好、不会拉伸变形后，正式打开摄像头。
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
                                Image.Plane plane = image.getPlanes()[0];
                                ByteBuffer buffer = plane.getBuffer();
                                byte[] temp = new byte[buffer.remaining()];
                                buffer.get(temp);
                                Bitmap src = BitmapFactory.decodeByteArray(temp, 0, temp.length);
                                //计算当前手机的旋转角度：90,180,270,360（0）四种角度
                                int rotation = mView.getDisplay().getRotation() * 90;
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
                                if (rotation == 90 || rotation == 270) {
                                    scaleX = dest.getWidth() * 1.0f / src.getWidth();
                                    scaleY = dest.getHeight() * 1.0f / src.getHeight();
                                } else {
                                    scaleX = dest.getWidth() * 1.0f / src.getHeight();
                                    scaleY = dest.getHeight() * 1.0f / src.getWidth();
                                }
//                                matrix.postScale(scaleX, scaleY, rectTo.centerX(), rectTo.centerY());
                                canvas.drawBitmap(src, matrix, new Paint());
                                callback.onGetStillPic(dest);
                                mTakeStillPicLock.release();
                                image.close();
                            } else {
                                callback.onFailToGetPic();
                            }
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
                                if (!mProcessLock.tryAcquire()) {
                                    return;
                                }
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
                                            showLog("第三步 成功，开始静态拍照", 0);
                                            if (mCurrentCaptureState.compareAndSet(CAPTURE_STATE_WAITING_LOCK, CAPTURE_STATE_FOCUSED)) {
                                                try {
                                                    CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                                    builder.addTarget(mTakeStillPicImageReader.getSurface());
                                                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                                    mCaptureSession.stopRepeating();
                                                    mCaptureSession.abortCaptures();
                                                    //在诺基亚上测试时发现需要使用setRepeatingRequest而不是request，否则成功拍照后就停止了，触发不了
                                                    // onCaptureCompleted或者onCaptureProgressed，不会到第五步
                                                    mCaptureSession.setRepeatingRequest(
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
                                            showLog("第三步 失败，重来", 0);
                                        }
                                        break;
                                    case CAPTURE_STATE_PIC_TAKEN:
                                        //第五步 拍照完成，返回预览
                                        showLog("第五步 拍照完成，返回预览", 0);
                                        if (mCurrentCaptureState.compareAndSet(CAPTURE_STATE_PIC_TAKEN, CAPTURE_STATE_PREVIEWING)) {
                                            try {
                                                mCaptureSession.stopRepeating();
                                                mCaptureSession.abortCaptures();
                                                mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                                                //乐视手机在这里可能会崩溃。
                                                mCaptureSession.setRepeatingRequest(
                                                        mRequestBuilder.build(),
                                                        null,
                                                        mBgThreadHandler
                                                );

                                            } catch (CameraAccessException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        break;

                                }
                                //别忘记开锁
                                mProcessLock.release();
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

    void closeCamera() {
        if (mCameraLock.tryAcquire()) {
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
                try {
                    mBgThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mBgThread = null;
            }
            mCameraLock.release();
        }
    }

    void showLog(String msg, int... logCodes) {
        LogUtil.showLog(getClass().getSimpleName(), msg, logCodes);
    }
}
