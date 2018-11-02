package tgi.com.androidcameramodule.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

public class ResizableSurfaceView extends SurfaceView {
    private int mTargetAspectWidth = -1;
    private int mTargetAspectHeight = -1;
    private static final int DEFAULT_WITH = 400;
    private static final int DEFAULT_HEIGHT = 400;

    public ResizableSurfaceView(Context context) {
        this(context, null);
    }

    public ResizableSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ResizableSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void resize(int aspectWidth, int aspectHeight) throws IllegalArgumentException {
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
        if (mTargetAspectWidth > 0 && mTargetAspectHeight > 0) {
            float currentRatio=width*1.f/height;
            float targetRatio= mTargetAspectWidth *1.f/ mTargetAspectHeight;
            if(currentRatio>targetRatio){
                width= (int) (height*1.f* mTargetAspectWidth / mTargetAspectHeight);
            }else {
                height= (int) (width*1.f* mTargetAspectHeight / mTargetAspectWidth);
            }
        }
        setMeasuredDimension(width,height);
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
}
