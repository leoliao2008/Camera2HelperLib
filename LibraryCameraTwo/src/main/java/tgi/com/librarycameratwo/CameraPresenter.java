package tgi.com.librarycameratwo;

import android.content.Context;
import android.graphics.ImageFormat;
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
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
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
    private Semaphore mLock=new Semaphore(1);//this is a thread safe lock to guarantee open camera/close camera are not executed simultaneously
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mRequestBuilder;
    private ImageReader mImageReader;


    public CameraPresenter(CameraView view) {
        mView = view;
        mCameraManager= (CameraManager) view.getContext().getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        mModel=new CameraModel(mCameraManager);
        mHandlerThread = new HandlerThread("CameraViewHandlerThread", Process.THREAD_PRIORITY_BACKGROUND);
        mHandlerThread.start();
        mHandler=new Handler(mHandlerThread.getLooper());
    }

    public void setSurfaceTextureListener() {
        mView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, final int height) {
                try {
                    boolean acquire = mLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
                    if(!acquire){
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
                                    mLock.release();
                                    mCamera=camera;
                                    try {
                                        Size optimalSize = mModel.getOptimalPreviewSize(
                                                mView.getWidth(),
                                                mView.getHeight(),
                                                mCamera.getId());
                                        mView.resize(600,800);

                                        mImageReader = ImageReader.newInstance(
                                                600,
                                                800,
                                                ImageFormat.JPEG,
                                                2
                                        );

                                        mModel.startPreview(
                                                mCamera,
                                                new Surface(surface),
                                                Arrays.asList(new Surface(surface),mImageReader.getSurface()),
                                                new PreviewSessionCallback(){
                                                    @Override
                                                    public void onSessionEstablished(CaptureRequest.Builder builder, CameraCaptureSession session) {
                                                        showLog("onSessionEstablished");
                                                        mRequestBuilder=builder;
                                                        mSession=session;
                                                    }

                                                    @Override
                                                    public void onFailToEstablishSession() {
                                                        showLog("onFailToEstablishSession");
                                                        closeCamera();
                                                    }
                                                },mHandler);

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
                                            "open camera: "+camera.getId()+" error code: "+error));
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

    private void closeCamera() {
        showLog("closeCamera");
        try {
            boolean acquire = mLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if(acquire){
                mRequestBuilder=null;

                if(mSession!=null){
                    mSession.close();
                    mSession=null;
                }
                if(mCamera!=null){
                    mCamera.close();
                    mCamera=null;
                }
                if(mImageReader!=null){
                    mImageReader.close();
                    mImageReader=null;
                }
                mHandlerThread.quitSafely();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            mView.handleError(e);
        }
    }

    public void takePic(TakePicCallback callback){
        if(mRequestBuilder==null||mSession==null){
            return;
        }
        try {
            mModel.takePic(mRequestBuilder,mSession,mImageReader,callback,mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mView.handleError(e);
        }
    }

    private void showLog(String msg){
        Log.e(getClass().getSimpleName(),msg);
    }
}
