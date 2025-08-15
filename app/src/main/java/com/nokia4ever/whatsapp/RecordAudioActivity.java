package com.nokia4ever.whatsapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import uk.me.hardill.volley.multipart.MultipartRequest;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.visualizer.amplitude.AudioRecordView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordAudioActivity extends AppCompatActivity {

    private static final String TAG = "RecordAudioActivity";

    private static final int REQUEST_AUDIO_PERMISSION_CODE=101;
    MediaRecorder mediaRecorder;
    MediaPlayer mediaPlayer;
    ImageView ibRecord;
    ImageView ibPlay;
    ImageView ibUpload;
    TextView tvTime;
    boolean isRecording = false;
    boolean isPlaying = false;
    int seconds = 0;
    int dummySeconds = 0;
    int playableSeconds = 0;
    String path = null;
    AudioRecordView audioRecordView;

    Handler handler, handler2;
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    private Contact selectedContact;
    private String serverUrl;
    private WhatsAppUser whatsAppUser;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_audio);

        serverUrl = getIntent().getStringExtra("ServerUrl");
        whatsAppUser = (WhatsAppUser)getIntent().getSerializableExtra("WhatsAppUser");
        selectedContact = (Contact)getIntent().getSerializableExtra("Contact");


        ibRecord = findViewById(R.id.ib_record);
        ibPlay = findViewById(R.id.ib_play);
        ibUpload = findViewById(R.id.ib_upload);
        tvTime = findViewById(R.id.tv_time);
        audioRecordView = (AudioRecordView) findViewById(R.id.audioRecordView);

        mediaPlayer = new MediaPlayer();

        ibRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkRecordingPermission()) {
                    if (!isRecording) {
                        isRecording = true;
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                mediaRecorder = new MediaRecorder();
                                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                                mediaRecorder.setOutputFile(getRecordingFilePath().getPath());
                                path = getRecordingFilePath().getPath();
                                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

                                try {
                                    mediaRecorder.prepare();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                mediaRecorder.start();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        playableSeconds = 0;
                                        seconds = 0;
                                        dummySeconds = 0;
                                        ibRecord.setImageDrawable(ContextCompat.getDrawable(RecordAudioActivity.this,
                                                R.drawable.ic_mic_black_24dp));
                                        runTimer();
                                        runTimer2();
                                    }
                                });
                            }
                        });
                    } else {
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                mediaRecorder.stop();
                                mediaRecorder.release();
                                mediaRecorder = null;
                                playableSeconds = seconds;
                                dummySeconds = seconds;
                                seconds = 0;
                                isRecording = false;

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        handler.removeCallbacksAndMessages(null);
                                        handler2.removeCallbacksAndMessages(null);
                                        ibRecord.setImageDrawable(ContextCompat.getDrawable(RecordAudioActivity.this,
                                                R.drawable.ic_mic_none_black_24dp));
                                    }
                                });
                            }
                        });
                    }
                } else {
                    requestRecordingPermission();

                }
            }
        }); //ibRecord button

        ibPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPlaying) {
                    if (path != null) {
                        try {
                            mediaPlayer.setDataSource(getRecordingFilePath().getPath());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "No recording present", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        mediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mediaPlayer.start();
                    isPlaying = true;
                    ibPlay.setImageDrawable(ContextCompat.getDrawable(RecordAudioActivity.this, R.drawable.ic_pause_black_24dp));
                    runTimer();
                    runTimer2();
                }
                else {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    mediaPlayer = null;
                    mediaPlayer = new MediaPlayer();
                    isPlaying = false;
                    seconds = 0;
                    handler.removeCallbacksAndMessages(null);
                    handler2.removeCallbacksAndMessages(null);
                    ibPlay.setImageDrawable(ContextCompat.getDrawable(RecordAudioActivity.this, R.drawable.ic_play_arrow_black_24dp));
                }
            }

        }); // ibPlay button

        ibUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RequestQueue requestQueue = Volley.newRequestQueue(RecordAudioActivity.this);
                Map<String, String> headers = new HashMap<String, String>();
                String url = serverUrl + "/api/upload";
                MultipartRequest request = new MultipartRequest(url, headers,
                        new Response.Listener<NetworkResponse>() {
                            @Override
                            public void onResponse(NetworkResponse response) {
                                Toast.makeText(RecordAudioActivity.this, "Upload successful", Toast.LENGTH_SHORT).show();
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Toast.makeText(RecordAudioActivity.this, error.toString(), Toast.LENGTH_SHORT).show();
                                Log.e(TAG, error.getMessage(), error);
                            }
                        });

                request.addPart(new MultipartRequest.FormPart("sender",whatsAppUser.getUser()));
                request.addPart(new MultipartRequest.FormPart("receiver",selectedContact.getId()));
                byte[] data = getBytesFromFile(getRecordingFilePath());
                request.addPart(new MultipartRequest.FilePart("media", "audio/mpeg", "wa_recording.mp3",data ));

                requestQueue.add(request);
            }
        });

    }

    public byte[] getBytesFromFile(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int count, total = 0;
            while ((count = fis.read(data, total, data.length - total)) > 0) {
                total += count;
            }
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage(), e);
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void runTimer2(){
        handler2 = new Handler();
        handler2.post(new Runnable() {
            @Override
            public void run() {
                int currentMaxAmplitude = 0;
                if(isRecording){
                    currentMaxAmplitude = mediaRecorder != null ? mediaRecorder.getMaxAmplitude() : 0;
                    audioRecordView.update(currentMaxAmplitude); // redraw view
                }

                handler2.postDelayed(this, 100);
            }
        });
    }

    private void runTimer(){
        handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                int minutes = (seconds%3600)/60;
                int secs = seconds%60;
                String time = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
                tvTime.setText(time);

                if(isRecording || (isPlaying && playableSeconds != -1)){
                    seconds++;
                    playableSeconds--;
                    if(playableSeconds == -1 && isPlaying){
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        isPlaying = false;
                        mediaPlayer = null;
                        mediaPlayer = new MediaPlayer();
                        playableSeconds = dummySeconds;
                        seconds=0;
                        handler.removeCallbacksAndMessages(null);
                        ibPlay.setImageDrawable(ContextCompat.getDrawable(RecordAudioActivity.this, R.drawable.ic_play_arrow_black_24dp));
                        return;
                    }


                }

                handler.postDelayed(this, 1000);

            }
        });
    }

    private File getRecordingFilePath(){
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File music = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File file = new File(music, "wa_recording.mp3");
        return file;
    }

    private void requestRecordingPermission(){
        ActivityCompat.requestPermissions(RecordAudioActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    private boolean checkRecordingPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_DENIED){
            requestRecordingPermission();
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_AUDIO_PERMISSION_CODE){
            if(grantResults.length > 0){
                boolean permissionToRecord = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if(permissionToRecord)
                {
                    Toast.makeText(getApplicationContext(), "Permissin Given", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(getApplicationContext(), "Permissin Denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
