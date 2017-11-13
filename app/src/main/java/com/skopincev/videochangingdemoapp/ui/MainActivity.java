package com.skopincev.videochangingdemoapp.ui;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.skopincev.videochangingdemoapp.R;
import com.skopincev.videochangingdemoapp.media_processing.AudioExtractor;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_TAKE_GALLERY_VIDEO = 1;

    static {
        System.loadLibrary("sp-lib");
    }

    public MainActivity() {
    }

    private native void initAudioPlayer(int samplerate, int buffersize, String audioFilePath, int audioFileOffset, int audioFileLength);
    private native void onPlayPause(boolean play);
    private native void onCentsChanged(int cents);
    private native void onTempoChanged(double tempo);

    private SeekBar sbPitchShift;
    private SeekBar sbSpeed;
    private TextView tvSpeed;
    private TextView tvCents;
    private FrameLayout flVideo;
    private VideoContentView videoView;

    private boolean playing;
    private String samplerateString = null;
    private String buffersizeString = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        // Get the device's sample rate and buffer size to enable low-latency Android audio output, if available.
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        if (samplerateString == null) samplerateString = "44100";
        if (buffersizeString == null) buffersizeString = "512";

        initUI();
    }

    private void chooseVideoFile(){
        Intent intent = new Intent();
        intent.setType("video/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select Video"), REQUEST_TAKE_GALLERY_VIDEO);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                Uri selectedVideoUri = data.getData();

                // MEDIA GALLERY
                String selectedVideoPath = getPath(selectedVideoUri);
                if (selectedVideoPath != null) {
                    //Extracting audio and configuring Superpowered audio player
                    String[] temp = selectedVideoPath.split("/");
                    String videoFileName = temp[temp.length - 1].split("\\.")[0];

                    String extractedAudioFileName = videoFileName + "_audio";
                    String extractedAudioFilePath = getExternalFilesDir(null).getAbsolutePath() + "/" + extractedAudioFileName + ".aac";
                    extractAudioFromVideo(selectedVideoPath, extractedAudioFilePath);

                    File audioFile = new File(extractedAudioFilePath);
                    if (audioFile.exists()){
                        int audioFileOffset = 0, audioFileLength = (int)audioFile.length();
                        // Arguments: path to the audio file, offset and length of the audio file, sample rate, audio buffer size.
                        initAudioPlayer(Integer.parseInt(samplerateString), Integer.parseInt(buffersizeString), extractedAudioFilePath, audioFileOffset, audioFileLength);
                    }

                    //Configuring video player
                    videoView.initMediaPlayer(selectedVideoPath);
                }
            }
        }
    }

    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Video.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            // HERE YOU WILL GET A NULLPOINTER IF CURSOR IS NULL
            // THIS CAN BE, IF YOU USED OI FILE MANAGER FOR PICKING THE MEDIA
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } else
            return null;
    }

    private void extractAudioFromVideo(String videoFilePath, String extractedAudioFilePath){
        AudioExtractor audioExtractor = new AudioExtractor();
        audioExtractor.extract(videoFilePath, extractedAudioFilePath);
    }

    private void initUI() {
        tvCents = findViewById(R.id.tv_cents);
        tvSpeed = findViewById(R.id.tv_speed);

        // TODO: 13.11.2017 implement audio playing

        sbPitchShift = findViewById(R.id.sb_pitch_shift);
        sbPitchShift.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int cents = progress - 1200;
                onCentsChanged(cents);
                tvCents.setText("cents: " + cents);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbSpeed = findViewById(R.id.sb_speed);
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                double tempo = ((double)progress - 50) / 100 + 1.00f;
                onTempoChanged(tempo);
                tvSpeed.setText("speed: " + tempo + "x");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        flVideo = findViewById(R.id.fl_video_container);
        videoView = new VideoContentView(this);
        flVideo.addView(videoView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.mi_choose_video:{
                chooseVideoFile();
                return true;
            }
            default: {
                return false;
            }
        }
    }
}
