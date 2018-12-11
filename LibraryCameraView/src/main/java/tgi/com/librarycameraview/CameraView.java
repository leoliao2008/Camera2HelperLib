package tgi.com.librarycameraview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;
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
public class CameraView extends TextureView {
    private CameraViewPresenter mPresenter;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private SizeChangeCallback mSizeChangeCallback;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setKeepScreenOn(true);
        mPresenter = new CameraViewPresenter(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            mPresenter.openCamera();
        } else {
            mPresenter.closeCamera();
        }
    }

    public void resetWidthHeightRatio(int optimalWidth, int optimalHeight) {
        mRatioWidth = optimalWidth;
        mRatioHeight = optimalHeight;
        //注意这里用invalidate是不行的
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 < mRatioWidth && 0 < mRatioHeight) {
            if (width < height * 1.0f * mRatioWidth / mRatioHeight) {//把图片缩小到整个视图里，保证预览时看到完整图片。
                height = (int) (width * 1.0f * mRatioHeight / mRatioWidth);
            } else {
                width = (int) (height * 1.0f * mRatioWidth / mRatioHeight);
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mSizeChangeCallback != null) {
            mSizeChangeCallback.onSizeChanged(w, h, oldw, oldh);
        }
    }


    public void onError(Exception e) {
        Toast.makeText(getContext().getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    void showLog(String msg, int... logCodes) {
        LogUtil.showLog(getClass().getSimpleName(), msg, logCodes);
    }

    interface SizeChangeCallback {
        void onSizeChanged(int w, int h, int oldw, int oldh);
    }

    void setSizeChangeCallback(SizeChangeCallback sizeChangeCallback) {
        mSizeChangeCallback = sizeChangeCallback;
    }
}
