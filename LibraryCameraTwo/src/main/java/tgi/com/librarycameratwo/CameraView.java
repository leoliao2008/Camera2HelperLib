package tgi.com.librarycameratwo;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;

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
        mPresenter.setSurfaceTextureListener();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = calculateDimension(widthMeasureSpec,CameraViewConstant.DEFAULT_VIEW_WIDTH);
        int height= calculateDimension(heightMeasureSpec,CameraViewConstant.DEFAULT_VIEW_HEIGHT);
        if (mTargetAspectWidth > 0 && mTargetAspectHeight > 0) {
            float currentRatio=width*1.f/height;
            float targetRatio= mTargetAspectWidth *1.f/ mTargetAspectHeight;
            if(currentRatio>targetRatio){
                height= (int) (width* mTargetAspectHeight*1.f / mTargetAspectWidth);
            }else {
                width= (int) (height* mTargetAspectWidth*1.f / mTargetAspectHeight);
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

    public void takePicture(TakePicCallback callback){
        mPresenter.takePic(callback);
    }

    public void resize(int aspectWidth, int aspectHeight) throws IllegalArgumentException {
        if (aspectWidth <= 0 || aspectHeight <= 0) {
            throw new IllegalArgumentException("Aspect value must be greater than 0!");
        }
        mTargetAspectWidth = aspectWidth;
        mTargetAspectHeight = aspectHeight;
        postInvalidate();
    }

    private void showLog(String msg){
        Log.e(getClass().getSimpleName(),msg);
    }

    public void handleError(Exception error) {
        showLog(error.getMessage());
    }
}
