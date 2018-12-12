package tgi.com.librarycameraview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

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
    private AtomicBoolean mIsFirstTimeInitResize = new AtomicBoolean(true);
    private CameraViewScaleType mScaleType = CameraViewScaleType.CENTER_INSIDE;

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


    void resetWidthHeightRatio(int optimalWidth, int optimalHeight) {
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
            if (mScaleType == CameraViewScaleType.CENTER_CROP) {
                if (width > height * 1.0f * mRatioWidth / mRatioHeight) {
                    height = (int) (width * 1.0f * mRatioHeight / mRatioWidth);
                } else {
                    width = (int) (height * 1.0f * mRatioWidth / mRatioHeight);
                }
            } else {
                if (width < height * 1.0f * mRatioWidth / mRatioHeight) {
                    height = (int) (width * 1.0f * mRatioHeight / mRatioWidth);
                } else {
                    width = (int) (height * 1.0f * mRatioWidth / mRatioHeight);
                }
            }
        }
        setMeasuredDimension(width, height);
        //经测试，除了第一次外，后续调节尺寸时，必须在这里执行回调，以保证不管尺寸是否改变，都会重新启动摄像头
        if (!mIsFirstTimeInitResize.get() && mSizeChangeListener != null) {
            mSizeChangeListener.onSizeChanged(width, height);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //经测试，第一次调节尺寸时，必须在这里执行回调，虽然尺寸结果一样，但只有在这里执行回调里面的内容，图像才不会拉伸。
        //原因未明。
        if (mSizeChangeListener != null) {
            if (mIsFirstTimeInitResize.compareAndSet(true, false)) {
                mSizeChangeListener.onSizeChanged(w, h);
            }
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

    public void setScaleType(CameraViewScaleType scaleType) {
        if(mScaleType==scaleType){
            return;
        }
        mScaleType = scaleType;
        mIsFirstTimeInitResize.set(true);
        mPresenter.closeCamera();
        mPresenter.openCamera();
    }

    public void takePic(TakeStillPicCallback callback) {
        mPresenter.takeStillPic(callback);
    }
}
