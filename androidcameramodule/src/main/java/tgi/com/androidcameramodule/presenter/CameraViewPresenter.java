package tgi.com.androidcameramodule.presenter;

import android.hardware.camera2.CameraManager;

import tgi.com.androidcameramodule.model.ICameraModel;
import tgi.com.androidcameramodule.model.MainCameraModel;
import tgi.com.androidcameramodule.view.ICameraView;

public class CameraViewPresenter {
    private ICameraView mCameraView;
    private ICameraModel mCameraModel;
    private CameraManager mCameraManager;
    private String mCameraId;

    public CameraViewPresenter(ICameraView cameraView,CameraManager cameraManager) {
        mCameraView = cameraView;
        mCameraManager=cameraManager;
//        mCameraModel=new MainCameraModel(mCameraManager);
    }

    public void openCamera(){
        if(mCameraId==null){
            mCameraId=mCameraModel.getCameraId();
        }
        mCameraModel.openCamera(mCameraId);
    }
}
