package tgi.com.librarycameratwo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

public class CameraPermissionHelper {

    private int mRequestCode;
    private AlertDialog mAlertDialog;

    public CameraPermissionHelper(int requestCode) {
        this.mRequestCode = requestCode;
    }

    public boolean hasCameraPermission(Context context) {
        int permission = context.getPackageManager().checkPermission(
                Manifest.permission.CAMERA,
                context.getPackageName()
        );
        return PackageManager.PERMISSION_GRANTED == permission;
    }

    public void requestCameraPermission(final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    mRequestCode
            );
        } else {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            activity,
                            "You need to grant camera permission to this app if you want to use its function.",
                            Toast.LENGTH_LONG)
                            .show();
                }
            });
        }
    }

    public boolean onRequestPermissionResult(final Activity activity, final int requestCode,
                                             String[] permissions, int[] results, Runnable onGranted,
                                             final Runnable onDenied) {
        boolean isConsumed=false;
        if(requestCode==mRequestCode){
            if(mAlertDialog!=null){
                mAlertDialog.dismiss();
            }
            isConsumed=true;
            int len=permissions.length;
            for(int i=0;i<len;i++){
                if(permissions[i].equals(Manifest.permission.CAMERA)){
                    if(results[i]!=PackageManager.PERMISSION_GRANTED){
                        AlertDialog.Builder builder=new AlertDialog.Builder(activity);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if(activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                                mAlertDialog = builder.setTitle("Camera Permission Request")
                                        .setMessage("Camera function is required in this app in order to acquire image content for analysis, if you choose to reject this request, the app will be closed.")
                                        .setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                mAlertDialog.dismiss();
                                                requestCameraPermission(activity);
                                            }
                                        })
                                        .setNegativeButton("Abort", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                mAlertDialog.dismiss();
                                                onDenied.run();
                                            }
                                        })
                                        .setCancelable(false)
                                        .create();
                                mAlertDialog.show();
                            }else {
                                requestCameraPermission(activity);
                            }
                        }else {
                            mAlertDialog= builder.setTitle("Camera Permission Request")
                                    .setMessage("This function needs camera permission to continue.")
                                    .setCancelable(false)
                                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mAlertDialog.dismiss();
                                            requestCameraPermission(activity);
                                        }
                                    })
                                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mAlertDialog.dismiss();
                                            onDenied.run();
                                        }
                                    })
                                    .create();
                            mAlertDialog.show();
                        }
                    }else {
                        onGranted.run();
                    }
                }
            }
        }

        return isConsumed;

    }


}
