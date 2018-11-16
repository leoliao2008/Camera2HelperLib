package tgi.com.librarycameratwo;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description:
 */
public class CameraModel {
    private CameraManager mManager;

    public CameraModel(CameraManager cameraManager) {
        mManager=cameraManager;
    }

    public String getFrontCamera() throws CameraAccessException {
        String[] cameraIdList = mManager.getCameraIdList();
        for(String id:cameraIdList){
            CameraCharacteristics chars = mManager.getCameraCharacteristics(id);
            Integer integer = chars.get(CameraCharacteristics.LENS_FACING);
            if(integer!=null&&integer==CameraCharacteristics.LENS_FACING_FRONT){
                return id;
            }
        }
        return null;

    }

    public String getRearCamera() throws CameraAccessException {
        String[] cameraIdList = mManager.getCameraIdList();
        for(String id:cameraIdList){
            CameraCharacteristics chars = mManager.getCameraCharacteristics(id);
            Integer integer = chars.get(CameraCharacteristics.LENS_FACING);
            if(integer!=null&&integer==CameraCharacteristics.LENS_FACING_BACK){
                return id;
            }
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    public void openCamera(String cameraId,CameraDevice.StateCallback callback, Handler handler) throws CameraAccessException {
        mManager.openCamera(cameraId,callback,handler);
    }


    public Size getOptimalPreviewSize(int originPreviewWidth, int originPreviewHeight, String cameraId)
            throws CameraAccessException {
        StreamConfigurationMap map = mManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        float originRatio=originPreviewWidth*1.0f/originPreviewHeight;
        //todo for now it will do, but must be improved later
        return Collections.max(
                Arrays.asList(sizes),
                new OptimalAspectRatioComparator(originRatio)
        );
    }


    public void startPreview(final CameraDevice camera, final List<Surface> surfaces,
                             final PreviewStateCallback callback, final Handler handler) throws CameraAccessException {
        camera.createCaptureSession(
                surfaces,
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {

                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                            for(Surface surface:surfaces){
                                builder.addTarget(surface);
                            }
                            session.setRepeatingRequest(builder.build(),null,handler);
                            callback.onConfigured(session,builder);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                            callback.onConfiguredFails(session);
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        callback.onConfiguredFails(session);

                    }
                },
                handler
        );

    }

    public Bitmap getImageFromImageReader(ImageReader reader) {
        Image image = reader.acquireNextImage();
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] data=new byte[buffer.remaining()];
        buffer.get(data);
        image.close();
        return BitmapFactory.decodeByteArray(data,0,data.length);
    }

    public void takePic(CaptureRequest.Builder builder, final CameraCaptureSession session,
                        TakePicSateCallback callback, Handler handler) throws CameraAccessException {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);
        session.setRepeatingRequest(
                builder.build(),
                new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                                 @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                        super.onCaptureStarted(session, request, timestamp, frameNumber);

                    }

                    @Override
                    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                    @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
                        super.onCaptureProgressed(session, request, partialResult);
//                        if(whatStageNow(session, request, partialResult)){
//                            requestToTakePic();
//                        }
                    }

                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                        super.onCaptureFailed(session, request, failure);
                    }
                },
                handler);

    }

    private void requestToTakePic() {

    }

    private String whatStageNow(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
        Integer integer = partialResult.get(CaptureResult.CONTROL_AF_STATE);
        if(integer==null){
            return CameraViewConstant.TAKE_PIC_STAGE_READY;
        }
        if(integer==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED||integer==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){


        }
        return CameraViewConstant.TAKE_PIC_STAGE_DEFAULT;
    }
}
