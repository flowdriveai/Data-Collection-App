package com.datacollectionapp.dcasdc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2, View.OnClickListener {

    private static final String TAG= "Main Activity";
    private static final int REQUEST_CAMERA_PERMISSION= 100;
    JavaCameraView cameraView;
    Mat mRGBA,mRGBAT;
    boolean captureStatus;
    TextView capture,counter,tv;
    String folderName="";
    List<String> saved=new ArrayList<>();
    public int seconds = 0;
    public int minutes = 0;
    Timer t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        getWindow(). addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        capture = findViewById(R.id.btn);
        capture.setOnClickListener(MainActivity.this);
        counter = findViewById(R.id.cnt);
        cameraView = findViewById(R.id.camera);
        cameraView.setVisibility(SurfaceView.VISIBLE);
        cameraView.setMaxFrameSize(1280,960);
        cameraView.setCvCameraViewListener(MainActivity.this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
        }
        tv = findViewById(R.id.timer);
        t = new Timer();
    }



    @SuppressLint({"SetTextI18n", "SimpleDateFormat"})
    @Override
    public void onClick(View v) {
        if(v == capture)
        {
            if(captureStatus){
                saved.clear();
                seconds = 0;
                minutes = 0;
                tv.setText(minutes +":"+seconds);
                counter.setText("Frames");
                captureStatus = false;
                capture.setText("CAPTURE");
                capture.setBackgroundColor(getResources().getColor(R.color.green));
                t.cancel();
            }
            else{
                saved.clear();
                folderName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                captureStatus = true;
                capture.setText("STOP");
                capture.setBackgroundColor(getResources().getColor(R.color.red));
                t.scheduleAtFixedRate(new TimerTask() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            tv.setText(minutes +":"+seconds);
                            seconds += 1;
                            if(seconds == 60)
                            {
                                tv.setText(minutes +":"+ seconds);
                                seconds=0;
                                minutes=minutes+1;
                            }
                            if(saved.size()>0){
                                counter.setText("Frames: "+saved.size());
                            }
                        });
                    }
                }, 0, 1000);
            }
        }
    }

    BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(MainActivity.this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == BaseLoaderCallback.SUCCESS) {
                cameraView.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRGBA = new Mat(height,width, CvType.CV_8UC4);
    }

    @Override
    public void onCameraViewStopped() {
        mRGBA.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRGBA = inputFrame.rgba();
        mRGBAT = mRGBA.t();
        Core.flip(mRGBA.t(),mRGBAT,1);
        Imgproc.resize(mRGBAT,mRGBAT,mRGBA.size());
        Mat tmp = mRGBA;
        Bitmap bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(tmp, bmp);

        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSSS").format(new Date());
        String fileName = timeStamp+".jpg";
        try {
            if(captureStatus)
            {
                saveImageToStorage(bmp,fileName);
            }
        }catch (IOException e){
            Log.e(TAG,e.getMessage());
        }
        return mRGBAT;
    }

    private void saveImageToStorage(Bitmap bitmap,String fileName) throws IOException {
        OutputStream imageOutStream;
        saved.add(fileName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/DCA-SDC/"+folderName+"/img/");

            Uri uri =
                    getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values);

            imageOutStream = getContentResolver().openOutputStream(uri);

        } else {

            String imagesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES). toString() + "/DCA-SDC/"+folderName+"/img/";
            File image = new File(imagesDir, fileName);
            imageOutStream = new FileOutputStream(image);
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageOutStream);
        imageOutStream.close();
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(MainActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
            else{
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        cameraView.disableView();
        super.onDestroy();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onPause() {
        captureStatus=false;
        seconds = 0;
        minutes = 0;
        tv.setText(minutes +":"+seconds);
        counter.setText("Frames");
        capture.setText("CAPTURE");
        capture.setBackgroundColor(getResources().getColor(R.color.green));
        counter.setText("Frames");
        super.onPause();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();
        captureStatus=false;
        seconds = 0;
        minutes = 0;
        tv.setText(minutes +":"+seconds);
        counter.setText("Frames");
        capture.setText("CAPTURE");
        capture.setBackgroundColor(getResources().getColor(R.color.green));
        counter.setText("Frames");
        if(OpenCVLoader.initDebug()) {
            Log.e(TAG, "Opencv Setup Done!");
            baseLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
        }
        else
        {
            Log.e(TAG,"Opencv Setup Failed!");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION,this,
                    baseLoaderCallback);
        }
    }
}