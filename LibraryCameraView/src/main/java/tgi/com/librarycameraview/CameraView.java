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
    private CameraViewScaleType mScaleType = CameraViewScaleType.CENTER_CROP;
    private AtomicBoolean mIsFirstTimeResize = new AtomicBoolean(true);

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

//    @Override
//    protected void onAttachedToWindow() {
//        super.onAttachedToWindow();
//        showLog("onAttachedToWindow");
//        mPresenter.openCamera();
//    }
//
//    @Override
//    protected void onDetachedFromWindow() {
//        super.onDetachedFromWindow();
//        showLog("onDetachedFromWindow");
//        mPresenter.closeCamera();
//    }

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
                //centerCrop: 不需要改变视图比例，改变视图尺寸，使其能刚好塞进图像中，显示图像中间部分。
                if (width > height * 1.0f * mRatioWidth / mRatioHeight) {
                    height = (int) (width * 1.0f * mRatioHeight / mRatioWidth);
                } else {
                    width = (int) (height * 1.0f * mRatioWidth / mRatioHeight);
                }
            } else {
                //centerInside: 改变视图比例，符合图像比例，把图像放进来，以完全显示图像。
                if (width < height * 1.0f * mRatioWidth / mRatioHeight) {
                    height = (int) (width * 1.0f * mRatioHeight / mRatioWidth);
                } else {
                    width = (int) (height * 1.0f * mRatioWidth / mRatioHeight);
                }
            }
        }
        setMeasuredDimension(width, height);
        //除了首次调整尺寸，后续的尺寸调整从这里获取数据，这样就算尺寸前后一致，也会触发回调的内容，重新打开摄像头。
        //这是考虑到程序从后台返回到前台时，能自动重新打开摄像头。
        showLog("onMeasure w="+width+" height="+height);
        if (!mIsFirstTimeResize.get() && mSizeChangeListener != null) {
            showLog("adopt size from onMeasure w="+width+" height="+height);
            mSizeChangeListener.onSizeChanged(width, height);
        }
//        if(mSizeChangeListener!=null){
//            mSizeChangeListener.onSizeChanged(width,height);
//        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //首次调整尺寸时从这里获取数据，虽然和onMeasure数值一样，但由于触发的时间点不一样，图像不会拉伸变形
        showLog("onSizeChanged w="+w+" h="+h);
        if (mSizeChangeListener != null) {
            if (mIsFirstTimeResize.compareAndSet(true, false)) {
                showLog("adopt size from onSizeChanged w="+w+" h="+h);
                mSizeChangeListener.onSizeChanged(w, h);
            }
        }
    }

    public void onError(Exception e) {
        Toast.makeText(getContext().getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
    }

    void showLog(String msg, int... logCodes) {
        LogUtil.showLog(msg, logCodes);
    }

    interface SizeChangeListener {
        void onSizeChanged(int w, int h);
    }

    void setSizeChangeListener(SizeChangeListener sizeChangeListener) {
        mSizeChangeListener = sizeChangeListener;
    }

    public void setScaleType(CameraViewScaleType scaleType) {
        if (mScaleType == scaleType) {
            return;
        }
        mScaleType = scaleType;
        mPresenter.closeCamera();
        mPresenter.openCamera();
    }

    public void takePic(TakeStillPicCallback callback) {
        mPresenter.takeStillPic(callback);
    }

    public void registerTensorFlowImageSubscriber(TensorFlowImageSubscriber subscriber) {
        mPresenter.registerTensorFlowImageSubscriber(subscriber);
    }

    public void unRegisterTensorFlowImageSubscriber() {
        mPresenter.unRegisterTensorFlowImageSubscriber();
    }

    public void enableDebug(boolean isDebugMode) {
        LogUtil.setDebugMode(isDebugMode);
    }
}
