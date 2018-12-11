package tgi.com.androidcamerademo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import java.util.Random;

import tgi.com.librarycameraview.CameraView;

public class NewCameraViewActivity extends AppCompatActivity {
    private CameraView mCameraView;

    public static void start(Context context) {
        Intent starter = new Intent(context, NewCameraViewActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_camera_view);
        mCameraView=findViewById(R.id.activity_new_camera_view_camera_view);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
//        if (hasFocus) {
//            View decorView = getWindow().getDecorView();
//            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                    | View.SYSTEM_UI_FLAG_FULLSCREEN
//                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//            );
//        }
    }

    public void testAutoFit(View view) {
        int w=new Random().nextInt(50);
        int h=new Random().nextInt(50);
        mCameraView.resetWidthHeightRatio(w,h);
    }
}
