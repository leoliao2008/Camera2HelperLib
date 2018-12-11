package tgi.com.androidcamerademo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;
import android.widget.Toast;

/**
 * <p><b>Author:</b></p>
 * <i>leo</i>
 * <p><b>Date:</b></p>
 * <i>On 4/12/2018</i>
 * <p><b>Project:</b></p>
 * <i>AndroidCameraDemo</i>
 * <p><b>Description:</b></p>
 */
public class AutoFitView extends TextureView {
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    public AutoFitView(Context context) {
        this(context, null);
    }

    public AutoFitView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    public void resetWidthHeightRatio(int optimalWidth, int optimalHeight) {
        mRatioWidth = optimalWidth;
        mRatioHeight = optimalHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }

    public void onError(Exception e) {
        Toast.makeText(getContext().getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}
