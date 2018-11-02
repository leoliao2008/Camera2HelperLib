package tgi.com.androidcameramodule.fragment;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import tgi.com.androidcameramodule.R;
import tgi.com.androidcameramodule.callback.PreviewCallback;
import tgi.com.androidcameramodule.model.MainCameraModel;
import tgi.com.androidcameramodule.utils.CameraPermissionHelper;
import tgi.com.androidcameramodule.view.ICameraView;
import tgi.com.androidcameramodule.widget.ResizableSurfaceView;

public class CameraPreviewFragment extends Fragment implements ICameraView {
    private ResizableSurfaceView mSurfaceView;
    //    private CameraViewPresenter mPresenter;
    private MainCameraModel mModel;
    private SurfaceHolder mPreViewSurfaceHolder;
    private String mCameraId;
    private Handler mHandler;
    private CameraPermissionHelper mPermissionHelper;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CameraManager manager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        mModel = new MainCameraModel(manager);
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
        mSurfaceView = view.findViewById(R.id.fragment_camera_preview_surface_view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }


    @Override
    public void onResume() {
        super.onResume();
        if (mPreViewSurfaceHolder == null) {
            mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mPreViewSurfaceHolder = holder;
                    startPreview();

                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    if (mCamera != null) {
                        mCamera.close();
                        mCamera = null;
                    }


                }
            });
        } else {
            startPreview();
        }
    }

    private CameraDevice mCamera;

    private void startPreview() {
        if (mCameraId == null) {
            mCameraId = mModel.getMainCameraId();
        }
        Size size = mModel.getClosestSupportedSize(
                new Size(mSurfaceView.getWidth(), mSurfaceView.getHeight()),
                mCameraId,
                ImageFormat.YUV_420_888);
        mSurfaceView.resize(size.getWidth(), size.getHeight());
        try {
            mModel.openMainCamera(
                    mCameraId,
                    mPreViewSurfaceHolder.getSurface(),
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

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
}
