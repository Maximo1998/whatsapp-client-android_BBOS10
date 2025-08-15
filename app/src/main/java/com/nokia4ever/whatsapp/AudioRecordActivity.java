package com.nokia4ever.whatsapp;

import android.Manifest;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import com.visualizer.amplitude.AudioRecordView;

import java.io.File;

public class AudioRecordActivity extends AppCompatActivity {

    private static final int MICROPHONE_PERMISSION_CODE = 200;
    MediaRecorder mediaRecorder;
    MediaPlayer mediaPlayer;
    AudioRecordView audioRecordView;

    Boolean isTimerEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record);

        audioRecordView = (AudioRecordView) findViewById(R.id.audioRecordView);


        if(isMicrophonePresent()){
            getMicrophonePermission();
        }

    }


    private void timer(){
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                int currentMaxAmplitude = mediaRecorder != null ? mediaRecorder.getMaxAmplitude() : 0;
                audioRecordView.update(currentMaxAmplitude); // redraw view

                if(isTimerEnabled)
                    handler.postDelayed(this, 100);
            }
        });
    }

    public void btnRecordPressed(View v){
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(getRecordingFilePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isTimerEnabled = true;
            timer();

            Toast.makeText(this, "Recording is started", Toast.LENGTH_LONG).show();

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private String getRecordingFilePath(){
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File musicDirectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File file = new File(musicDirectory, "testRecordingFile" + ".mp3");
        return file.getPath();
    }

    public void btnStopPressed(View v){
        mediaRecorder.stop();
        mediaRecorder.release();
        mediaRecorder = null;

        audioRecordView.recreate();
        isTimerEnabled=false;

        Toast.makeText(this, "Recording is stopped", Toast.LENGTH_LONG).show();
    }

    public void btnPlayPressed(View v){
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getRecordingFilePath());
            mediaPlayer.prepare();
            mediaPlayer.start();

            Toast.makeText(this, "Recording is playing", Toast.LENGTH_LONG).show();

        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private boolean isMicrophonePresent(){
        if(this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)){
            return true;
        }
        else {
            return false;
        }
    }

    private void getMicrophonePermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_DENIED){

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_CODE);
        }
    }
}
