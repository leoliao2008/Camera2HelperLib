package tgi.com.androidcamerademo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.RadioGroup;

public class AutoFitTestActivity extends AppCompatActivity {

    private AutoFitView mAutoFitView;
    private RadioGroup mRadioGroup;

    public static void start(Context context) {
        Intent starter = new Intent(context, AutoFitTestActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_fit_test);
        mAutoFitView = findViewById(R.id.auto_fit_view);
        mRadioGroup = findViewById(R.id.radio_group);


        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int w = -1;
                int h = -1;
                switch (checkedId) {
                    case R.id.radio_btn_1v1:
                        w = h = 1;
                        break;
                    case R.id.radio_btn_2v6:
                        w = 2;
                        h = 6;
                        break;
                    case R.id.radio_btn_4v6:
                        w = 4;
                        h = 6;
                        break;
                    case R.id.radio_btn_16v4:
                        w = 16;
                        h = 4;
                        break;
                    case R.id.radio_btn_16v8:
                        w = 16;
                        h = 8;
                        break;
                    default:
                        break;
                }
                if (w > 0 && h > 0) {
                    mAutoFitView.resetWidthHeightRatio(w, h);
                }

            }
        });
    }
}
