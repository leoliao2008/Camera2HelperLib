package tgi.com.androidcamerademo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int permission = checkSelfPermission(Manifest.permission.CAMERA);
            if(permission!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.CAMERA},123 );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==123){
            if(grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                finish();
            }
        }
    }

    public void toLib1(View view) {
        CameraViewActivity.start(this);
    }

    public void toLib2(View view) {
        NewCameraViewActivity.start(this);
    }

    public void toAutoFitTest(View view) {
        AutoFitTestActivity.start(this);
    }
}
