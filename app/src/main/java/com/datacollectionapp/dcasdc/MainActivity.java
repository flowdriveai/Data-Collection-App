package com.datacollectionapp.dcasdc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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
import androidx.annotation.RequiresApi;
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener,CameraBridgeViewBase.CvCameraViewListener2, View.OnClickListener {

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
    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_main);
        getWindow(). addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
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

        if(SharedPrefManager.getInstance(getApplicationContext()).getKeyInit()){
            writeInfo();
        }

        tv = findViewById(R.id.timer);
        t = new Timer();
    }

    private void writeInfo(){
        final String info = "Data is in the following order-> " +
                "\n\n" +
                "Azimuth (degrees of rotation about the -z axis). This is the angle between the device's current compass direction and magnetic north. If the top edge of the device faces magnetic north, the azimuth is 0 degrees; if the top edge faces south, the azimuth is 180 degrees. Similarly, if the top edge faces east, the azimuth is 90 degrees, and if the top edge faces west, the azimuth is 270 degrees." +
                "\n\n" +
                "Pitch (degrees of rotation about the x axis). This is the angle between a plane parallel to the device's screen and a plane parallel to the ground. If you hold the device parallel to the ground with the bottom edge closest to you and tilt the top edge of the device toward the ground, the pitch angle becomes positive. Tilting in the opposite direction— moving the top edge of the device away from the ground—causes the pitch angle to become negative. The range of values is -180 degrees to 180 degrees" +
                "\n\n" +
                "Roll (degrees of rotation about the y axis). This is the angle between a plane perpendicular to the device's screen and a plane perpendicular to the ground. If you hold the device parallel to the ground with the bottom edge closest to you and tilt the left edge of the device toward the ground, the roll angle becomes positive. Tilting in the opposite direction—moving the right edge of the device toward the ground— causes the roll angle to become negative. The range of values is -90 degrees to 90 degrees." +
                "\n\n\n" +
                "Note that these angles work off of a different coordinate system than the one used in aviation (for yaw, pitch, and roll). In the aviation system, the x axis is along the long side of the plane, from tail to nose.";
        try {
            File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DCA-SDC/Instruction/");
            if (!root.exists()) {
                root.mkdirs();
            }
            File file = new File(root, "info.txt");
            FileWriter writer = new FileWriter(file);
            writer.append(info);
            writer.flush();
            writer.close();
            Log.e(TAG,"Details Saved");
            SharedPrefManager.getInstance(getApplicationContext()).setKeyInit(false);
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
        }
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
                tv.setText("");
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRGBA = inputFrame.rgba();
        mRGBAT = mRGBA.t();
        Core.flip(mRGBA.t(),mRGBAT,1);
        Imgproc.resize(mRGBAT,mRGBAT,mRGBA.size());

        @SuppressLint("SimpleDateFormat") String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSSS").format(new Date());
        try {
            updateOrientationAngles();
            if(captureStatus && orientationAngles!=null)
            {
                String s = ""+orientationAngles[0]+" "+orientationAngles[1]+" "+orientationAngles[2]+" ";
                Bitmap bmp = Bitmap.createBitmap(mRGBA.cols(), mRGBA.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(mRGBA, bmp);
                saveImageToStorage(bmp, timeStamp, s);
            }
        }catch (IOException e){
            Log.e(TAG,e.getMessage());
        }
        mRGBA.release();
        return mRGBAT;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void saveImageToStorage(Bitmap bitmap, String fileName, String d) throws IOException {
        OutputStream imageOutStream;
        saved.add(fileName+".jpg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName+".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/DCA-SDC/"+folderName+"/img/");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);

            imageOutStream = getContentResolver().openOutputStream(uri);
        } else {

            String imagesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES). toString() + "/DCA-SDC/"+folderName+"/img/";
            File image = new File(imagesDir, fileName+".jpg");
            imageOutStream = new FileOutputStream(image);
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, imageOutStream);
        imageOutStream.close();

        try {
            File root = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DCA-SDC/"+folderName+"/data/");
            if (!root.exists()) {
                root.mkdirs();
            }
            File file = new File(root, fileName+".txt");
            FileWriter writer = new FileWriter(file);
            writer.append(d);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
        }
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
            }
            else{
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this,MainActivity.class));
            }
            finish();
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
        tv.setText("");
        counter.setText("Frames");
        capture.setText("CAPTURE");
        capture.setBackgroundColor(getResources().getColor(R.color.green));
        counter.setText("Frames");
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onResume() {
        super.onResume();
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        captureStatus=false;
        seconds = 0;
        minutes = 0;
        tv.setText("");
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    public void updateOrientationAngles() {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(rotationMatrix, null,
                accelerometerReading, magnetometerReading);

        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles);

        // "orientationAngles" now has up-to-date information.
    }
}