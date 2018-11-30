package tgi.com.librarycameratwo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/**
 * Author: leo
 * Data: On 16/11/2018
 * Project: AndroidCameraDemo
 * Description: This view encapsulate various camera 2 functions. It was with reference with
 * this book: <a href="https://commonsware.com/Android/">The Busy Coder's Guide to Android Development</a>
 */
public class CameraView extends TextureView {
    private int mTargetAspectWidth = -1;
    private int mTargetAspectHeight = -1;
    private CameraPresenter mPresenter;
    private DynamicImageCaptureCallback mDynamicImageCaptureCallback;

    public CameraView(Context context) {
        this(context,null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setKeepScreenOn(true);
        mPresenter=new CameraPresenter(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mPresenter.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mPresenter.onDetachedFromWindow();
        mDynamicImageCaptureCallback=null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        int width = calculateDimension(widthMeasureSpec,CameraViewConstant.DEFAULT_VIEW_WIDTH);
//        int height= calculateDimension(heightMeasureSpec,CameraViewConstant.DEFAULT_VIEW_HEIGHT);
//        if (mTargetAspectWidth > 0 && mTargetAspectHeight > 0) {
//            float currentRatio=width*1.f/height;
//            float targetRatio= mTargetAspectWidth *1.f/ mTargetAspectHeight;
//            if(currentRatio<targetRatio){
//                height= (int) (width* mTargetAspectHeight*1.f / mTargetAspectWidth);
//            }else {
//                width= (int) (height* mTargetAspectWidth*1.f / mTargetAspectHeight);
//            }
//        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if(mTargetAspectHeight>0&&mTargetAspectWidth>0){
            if (width > height * mTargetAspectWidth / mTargetAspectHeight) {
                height=width * mTargetAspectHeight / mTargetAspectWidth;
            } else {
                width=height*mTargetAspectWidth/mTargetAspectHeight;
            }
        }
        setMeasuredDimension(width,height);
    }



    private int calculateDimension(int measureSpec, int defaultValue) {
        int mode = MeasureSpec.getMode(measureSpec);
        int dimen=-1;
        switch (mode) {
            case MeasureSpec.EXACTLY:
                //match parent or exact value
                dimen=MeasureSpec.getSize(measureSpec);
                break;
            case MeasureSpec.AT_MOST:
            case MeasureSpec.UNSPECIFIED:
                //wrap content
                dimen=defaultValue;
                break;
        }
        return dimen;
    }

    public void takePicture(final TakePicCallback callback){
        disableDynamicProcessing();
        mPresenter.takePic(new TakePicCallback() {
            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }

            @Override
            public void onImageTaken(Bitmap image) {
                callback.onImageTaken(image);
                if(mDynamicImageCaptureCallback!=null){
                    enableDynamicProcessing(mDynamicImageCaptureCallback);
                }

            }
        });
    }


    public void enableDynamicProcessing(DynamicImageCaptureCallback callback){
        mDynamicImageCaptureCallback=callback;
        mPresenter.enableDynamicImageProcessing(callback);
    }

    public void disableDynamicProcessing() {
        mPresenter.disableDynamicImageProcessing();
    }

    void resize(int aspectWidth, int aspectHeight) throws IllegalArgumentException {
        if (aspectWidth <= 0 || aspectHeight <= 0) {
            throw new IllegalArgumentException("Aspect value must be greater than 0!");
        }
        mTargetAspectWidth = aspectWidth;
        mTargetAspectHeight = aspectHeight;
        postInvalidate();
    }

    private void showLog(String msg){
        if(CameraViewConstant.IS_DEBUG_MODE){
            Log.e(getClass().getSimpleName(),msg);
        }
    }

    void handleError(Exception error) {
        showLog(error.getMessage());
    }

    public interface DynamicImageCaptureCallback{
        void onGetDynamicImage(Bitmap image);
    }
}
