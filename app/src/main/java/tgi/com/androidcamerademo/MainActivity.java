package tgi.com.androidcamerademo;

import android.graphics.Bitmap;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;

import tgi.com.androidcameramodule.fragment.CameraPreviewFragment;

public class MainActivity extends AppCompatActivity {
    private CameraPreviewFragment mFragment;
    private ImageView mImageView;
    private FrameLayout mFrameLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mImageView=findViewById(R.id.image_view);
        mFrameLayout=findViewById(R.id.frame_layout);
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for(Fragment fragment:fragments){
            if(fragment instanceof CameraPreviewFragment){
                mFragment= (CameraPreviewFragment) fragment;
                break;
            }
        }
    }

    public void takePic(View view) {
        mFragment.takePic(new CameraPreviewFragment.TakePicListener() {
            @Override
            public void onPictureTaken(Bitmap pic) {
                mFrameLayout.setVisibility(View.VISIBLE);
                mImageView.setImageBitmap(pic);
            }
        });
    }

    public void backToCamera(View view) {
        mFrameLayout.setVisibility(View.GONE);

    }
}
