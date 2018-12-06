package tgi.com.androidcamerademo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
    }

    public void toLib1(View view) {
        CameraViewActivity.start(this);
    }

    public void toLib2(View view) {
        NewCameraViewActivity.start(this);
    }
}
