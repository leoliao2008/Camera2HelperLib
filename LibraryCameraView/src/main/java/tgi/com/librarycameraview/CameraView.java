package tgi.com.librarycameraview;

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
public class CameraView extends TextureView {
    private CameraViewPresenter mPresenter;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private SizeChangeListener mSizeChangeListener;

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
        //每次requestLayout()时都触发这个回调，在回调中调整预览图尺寸。如果在onSizeChange中调用这个回调，前后尺寸一致时不会触发，
        //但我需要每次都触发。
        if (mSizeChangeListener != null) {
            mSizeChangeListener.onSizeChanged(width, height);
        }
    }


    public void onError(Exception e) {
        Toast.makeText(getContext().getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    void showLog(String msg, int... logCodes) {
        LogUtil.showLog(getClass().getSimpleName(), msg, logCodes);
    }

    interface SizeChangeListener {
        void onSizeChanged(int w, int h);
    }

    void setSizeChangeListener(SizeChangeListener sizeChangeListener) {
        mSizeChangeListener = sizeChangeListener;
    }
}
