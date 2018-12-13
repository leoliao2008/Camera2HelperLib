package tgi.com.androidcamerademo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import tgi.com.librarycameraview.CameraView;
import tgi.com.librarycameraview.CameraViewScaleType;
import tgi.com.librarycameraview.TakeStillPicCallback;
import tgi.com.librarycameraview.TensorFlowImageSubscriber;

public class NewCameraViewActivity extends AppCompatActivity {
    private CameraView mCameraView;
    private ImageView mPic;
    private ToggleButton mTgBtnPic;
    private Switch mSwitch;
    private ImageView mIvTensorFlowImage;

    public static void start(Context context) {
        Intent starter = new Intent(context, NewCameraViewActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_camera_view);
        mCameraView=findViewById(R.id.activity_new_camera_view_camera_view);
        mPic=findViewById(R.id.activity_new_camera_view_iv_pic);
        mTgBtnPic=findViewById(R.id.activity_new_camera_view_tgbtn_pic);
        mSwitch=findViewById(R.id.activity_new_camera_view_switch_scale_type);
        mIvTensorFlowImage=findViewById(R.id.activity_new_camera_view_iv_tensor_flow_image);

        mTgBtnPic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    mPic.setVisibility(View.VISIBLE);
                }else {
                    mPic.setVisibility(View.GONE);
                }
            }
        });

        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(mSwitch.isChecked()){
                    mCameraView.setScaleType(CameraViewScaleType.CENTER_CROP);
                }else {
                    mCameraView.setScaleType(CameraViewScaleType.CENTER_INSIDE);
                }
            }
        });

        mCameraView.registerTensorFlowImageSubscriber(new TensorFlowImageSubscriber(){
            @Override
            public void onGetDynamicImage(final Bitmap image) {
                super.onGetDynamicImage(image);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mIvTensorFlowImage.setImageBitmap(image);
                    }
                });
            }
        });
    }


    public void takePic(View view) {
        mCameraView.takePic(new TakeStillPicCallback(){
            @Override
            public void onGetStillPic(final Bitmap bitmap) {
                super.onGetStillPic(bitmap);
                Display display = getWindowManager().getDefaultDisplay();
                DisplayMetrics metrics=new DisplayMetrics();
                display.getMetrics(metrics);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPic.setImageBitmap(bitmap);
                        mTgBtnPic.setChecked(true);
                    }
                });

            }

            @Override
            public void onFailToGetPic() {
                super.onFailToGetPic();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NewCameraViewActivity.this,"onFailToGetPic",Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraView.unRegisterTensorFlowImageSubscriber();
    }
}
