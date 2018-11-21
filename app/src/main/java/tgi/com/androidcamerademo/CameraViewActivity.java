package tgi.com.androidcamerademo;

import android.graphics.Bitmap;
import android.media.ImageReader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import tgi.com.librarycameratwo.CameraPresenter;
import tgi.com.librarycameratwo.CameraView;
import tgi.com.librarycameratwo.TakePicCallback;

public class CameraViewActivity extends AppCompatActivity {
    private ImageView mImageView;
    private CameraView mCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);
        mImageView=findViewById(R.id.activity_camera_view_iv_pic);
        mCameraView=findViewById(R.id.activity_camera_view_camera_view);
    }

    public void takePic(View view) {
        mCameraView.takePicture(new TakePicCallback() {
            @Override
            public void onError(Exception e) {
                Log.e("CameraViewActivity",e.getMessage());
            }

            @Override
            public void onImageTaken(final Bitmap image) {
                mImageView.post(new Runnable() {
                    @Override
                    public void run() {
                        mImageView.setVisibility(View.VISIBLE);
                        mImageView.setImageBitmap(image);
                    }
                });
            }
        });

    }

    public void swapLayer(View view) {
        mImageView.setVisibility(mImageView.getVisibility()==View.VISIBLE?View.INVISIBLE:View.VISIBLE);
    }
}
