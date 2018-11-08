package tgi.com.androidcameramodule.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

public class ResizableTextureView extends TextureView {
    private int mTargetAspectWidth = -1;
    private int mTargetAspectHeight = -1;
    private static final int DEFAULT_WITH = 400;
    private static final int DEFAULT_HEIGHT = 400;

    public ResizableTextureView(Context context) {
        this(context, null);
    }

    public ResizableTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ResizableTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAspectRatio(int aspectWidth, int aspectHeight) throws IllegalArgumentException {
        if (aspectWidth <= 0 || aspectHeight <= 0) {
            throw new IllegalArgumentException("Aspect value must be greater than 0!");
        }
        mTargetAspectWidth = aspectWidth;
        mTargetAspectHeight = aspectHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = calculateDimension(widthMeasureSpec,DEFAULT_WITH);
        int height= calculateDimension(heightMeasureSpec,DEFAULT_HEIGHT);
        showLog("before width="+width+" height="+height+" with/height="+width*1.0f/height);
        if (mTargetAspectWidth > 0 && mTargetAspectHeight > 0) {
            float currentRatio=width*1.f/height;
            float targetRatio= mTargetAspectWidth *1.f/ mTargetAspectHeight;
            if(currentRatio>targetRatio){
                height= (int) (width* mTargetAspectHeight*1.0f / mTargetAspectWidth);

            }else {
                width= (int) (height* mTargetAspectWidth*1.0f / mTargetAspectHeight);
            }
        }
        setMeasuredDimension(width,height);
        showLog("after width="+width+" height="+height+" with/height="+width*1.0f/height);
    }

    private int calculateDimension(int measureSpec,int defaultValue) {
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

    private void showLog(String msg){
//        Log.e(getClass().getSimpleName(),msg);
    }
}
