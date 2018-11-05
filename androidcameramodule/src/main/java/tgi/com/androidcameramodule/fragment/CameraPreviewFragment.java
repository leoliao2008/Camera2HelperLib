package tgi.com.androidcameramodule.fragment;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
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

import tgi.com.androidcameramodule.R;
import tgi.com.androidcameramodule.callback.PreviewCallback;
import tgi.com.androidcameramodule.model.CameraModule;
import tgi.com.androidcameramodule.utils.CameraPermissionHelper;
import tgi.com.androidcameramodule.view.ICameraView;
import tgi.com.androidcameramodule.widget.ResizableTextureView;

public class CameraPreviewFragment extends Fragment implements ICameraView {
    private ResizableTextureView mTextureView;
    //    private CameraViewPresenter mPresenter;
    private CameraModule mModel;
    private SurfaceTexture mPreViewSurfaceTexture;
    private String mCameraId;
    private Handler mHandler;
    private CameraPermissionHelper mPermissionHelper;
    private Matrix mMatrix;
    private Size mSize;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        mModel = new CameraModule(manager);
        mHandler = new Handler();
        mPermissionHelper = new CameraPermissionHelper(123);
        boolean hasCameraPermission = mPermissionHelper.hasCameraPermission(getActivity());
        if (!hasCameraPermission) {
            mPermissionHelper.requestCameraPermission(getActivity());
        }
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
                    Matrix matrix = mModel.adjustPreviewMatrixDueToRotation(width, height, mSize.getWidth(), mSize.getHeight(), rotation);
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
        mCamera.close();
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
        configureCameraOutput();
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = mModel.adjustPreviewMatrixDueToRotation(width, height, mSize.getWidth(), mSize.getHeight(), rotation);
        mTextureView.setTransform(matrix);
        try {
            mModel.openMainCamera(
                    mCameraId,
                    new Surface(mPreViewSurfaceTexture),
                    new PreviewCallback() {
                        @Override
                        public void onCameraOpen(CameraDevice camera) {
                            super.onCameraOpen(camera);
                            mCamera = camera;
                        }
                    },
                    null,
                    mHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureCameraOutput() {
        mSize = mModel.getOptimizedSize(new Size(mTextureView.getWidth(), mTextureView.getHeight()), mCameraId);
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTextureView.resize(mSize.getWidth(), mSize.getHeight());
            mPreViewSurfaceTexture.setDefaultBufferSize(mTextureView.getHeight(), mTextureView.getWidth());
        } else {
            mTextureView.resize(mSize.getHeight(), mSize.getWidth());
            mPreViewSurfaceTexture.setDefaultBufferSize(mTextureView.getWidth(), mTextureView.getHeight());
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mPermissionHelper.onRequestPermissionResult(
                getActivity(),
                requestCode,
                permissions,
                grantResults
        );
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void showLog(String msg) {
        Log.e("preview fragment", msg);
    }
}
