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
import android.widget.SeekBar;
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
import java.util.concurrent.TimeUnit;

public class PlayAudioActivity extends AppCompatActivity {

    private static final String TAG = "PlayAudioActivity";

    MediaPlayer mediaPlayer;
    ImageView ibPlay;
    TextView tvCurrentTime, tvTotalTime;
    SeekBar sbProgress;
    boolean isPlaying = false;
    int seconds = 0;

    Handler handler;
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    private Message selectedMessage;
    private String serverUrl;
    private WhatsAppUser whatsAppUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_audio);

        serverUrl = getIntent().getStringExtra("ServerUrl");
        whatsAppUser = (WhatsAppUser)getIntent().getSerializableExtra("WhatsAppUser");
        selectedMessage = (Message)getIntent().getSerializableExtra("Message");


        ibPlay = findViewById(R.id.ib_play);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        sbProgress = findViewById(R.id.sb_progress);
        sbProgress.setProgress(0);

        mediaPlayer = new MediaPlayer();



        ibPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mediaPlayer.isPlaying()) {
                    try {
                        //String audioUrl = serverUrl + "/api/mediafile/" + selectedMessage.getId() + ".wav";
                        String audioUrl = serverUrl + "/api/mediafile/" + selectedMessage.getId() + ".mp3";
                        Log.i(TAG, "onClick: " + audioUrl);

                        mediaPlayer.reset();
                        mediaPlayer.setDataSource(audioUrl);
                        mediaPlayer.prepare();

                        sbProgress.setProgress(0);
                        sbProgress.setMax(mediaPlayer.getDuration());
                        tvTotalTime.setText(convertToMMSS(mediaPlayer.getDuration()));

                        mediaPlayer.start();
                        ibPlay.setImageDrawable(ContextCompat.getDrawable(PlayAudioActivity.this, R.drawable.ic_pause_black_24dp));
                        runTimer();


                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }

                }
                else {
                    mediaPlayer.stop();
                    mediaPlayer.release();

                    handler.removeCallbacksAndMessages(null);
                    ibPlay.setImageDrawable(ContextCompat.getDrawable(PlayAudioActivity.this, R.drawable.ic_play_arrow_black_24dp));
                }
            }

        }); // ibPlay button



    }



    private static String convertToMMSS(int duration){
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(duration) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(duration) % TimeUnit.HOURS.toSeconds(1)
        );
    }

    private void runTimer(){
        handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {

                if(mediaPlayer.isPlaying()){

                    tvCurrentTime.setText(convertToMMSS(mediaPlayer.getCurrentPosition()));
                    sbProgress.setProgress(mediaPlayer.getCurrentPosition());
                }
                else {
                    ibPlay.setImageDrawable(ContextCompat.getDrawable(PlayAudioActivity.this, R.drawable.ic_play_arrow_black_24dp));
                }

                handler.postDelayed(this, 1000);

            }
        });
    }






}
