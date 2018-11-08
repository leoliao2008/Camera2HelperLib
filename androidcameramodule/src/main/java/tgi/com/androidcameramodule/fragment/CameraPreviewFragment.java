package tgi.com.androidcameramodule.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.text.Format;

import tgi.com.androidcameramodule.R;
import tgi.com.androidcameramodule.callback.CameraCallback;
import tgi.com.androidcameramodule.model.CameraModule;
import tgi.com.androidcameramodule.utils.CameraPermissionHelper;
import tgi.com.androidcameramodule.widget.ResizableTextureView;

public class CameraPreviewFragment extends Fragment{
    private ResizableTextureView mTextureView;
    private CameraModule mModel;
    private SurfaceTexture mPreViewSurfaceTexture;
    private String mCameraId;
    private Handler mHandler;
    private CameraPermissionHelper mPermissionHelper;
    private Size mSize;
    private HandlerThread mBackgroundThread;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);

        mBackgroundThread = new HandlerThread("background thread");
        mBackgroundThread.start();
        mHandler = new Handler(mBackgroundThread.getLooper());
        mPermissionHelper = new CameraPermissionHelper(123);
        mModel = new CameraModule(
                manager,
                new CameraCallback(){
            @Override
            public void onCameraOpen(CameraDevice camera) {
                super.onCameraOpen(camera);
                mCamera=camera;
            }

            @Override
            public void onImageAvailable(Bitmap bitmap) {
                super.onImageAvailable(bitmap);
                showLog("image scanner got images! size="+bitmap.getByteCount());
            }

            @Override
            public void onError(String errorMsg) {
                super.onError(errorMsg);
            }
        },mHandler);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera_preview_layout, container, false);
        mTextureView = view.findViewById(R.id.fragment_camera_preview_texture_view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }


    @Override
    public void onResume() {
        super.onResume();
        boolean hasCameraPermission = mPermissionHelper.hasCameraPermission(getActivity());
        if (!hasCameraPermission) {
            mPermissionHelper.requestCameraPermission(getActivity());
        }else {
            initCameraPreview();
        }
    }

    private void initCameraPreview() {
        if (!mTextureView.isAvailable()) {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    mPreViewSurfaceTexture = surface;
                    startPreview();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                    Matrix matrix = mModel.getTransformMatrix(width, height, rotation, mSize);
                    mTextureView.setTransform(matrix);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (mCamera != null) {
                        mCamera.close();
                        mCamera = null;
                    }
                    mPreViewSurfaceTexture.release();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
        } else {
            startPreview();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPreView();
    }

    private void stopPreView() {
        if(mCamera!=null){
            mCamera.close();
        }
        if(mBackgroundThread!=null){
            mBackgroundThread.quit();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    private CameraDevice mCamera;

    private void startPreview() {
        if (mCameraId == null) {
            mCameraId = mModel.getMainCameraId();
        }

        int width = mTextureView.getWidth();
        int height = mTextureView.getHeight();
        configureCameraOutput(width,height);

        try {
            mModel.openMainCamera(
                    mCameraId,
                    new Surface(mPreViewSurfaceTexture),
                    mSize);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void takePic(final TakePicListener listener){
        mModel.takePic(
                mCamera,
                getActivity().getWindowManager().getDefaultDisplay().getRotation(),
                new TakePicListener() {
                    @Override
                    public void onPictureTaken(final Bitmap pic) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                listener.onPictureTaken(pic);
                            }
                        });
                    }
                }
        );
    }


    private void configureCameraOutput(int textureWidth, int textureHeight) {
        Point screenSize=new Point();
        getActivity().getWindowManager().getDefaultDisplay().getSize(screenSize);
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mSize=mModel.chooseOptimalPreviewSize(
                textureWidth,
                textureHeight,
                mCameraId,
                screenSize,
                rotation
        );
        if(isLandscape){
            mTextureView.setAspectRatio(mSize.getWidth(), mSize.getHeight());
        }else {
            mTextureView.setAspectRatio(mSize.getHeight(), mSize.getWidth());
        }
        Matrix matrix = mModel.getTransformMatrix(textureWidth, textureHeight, rotation, mSize);
        mTextureView.setTransform(matrix);
        mPreViewSurfaceTexture.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean isGranted = mPermissionHelper.onRequestPermissionResult(
                getActivity(),
                requestCode,
                permissions,
                grantResults
        );
        if(isGranted){
            initCameraPreview();
        }else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showLog(String msg) {
        Log.e("preview fragment", msg);
    }

    public interface TakePicListener{
        void onPictureTaken(Bitmap pic);
    }
}
