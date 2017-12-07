package com.skopincev.videochangingdemoapp.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;
import com.skopincev.videochangingdemoapp.BundleConst;
import com.skopincev.videochangingdemoapp.R;
import com.skopincev.videochangingdemoapp.media_processing.AACEncoder;
import com.skopincev.videochangingdemoapp.media_processing.AudioExtractor;
import com.skopincev.videochangingdemoapp.media_processing.MediaMerger;
import com.skopincev.videochangingdemoapp.media_processing.MediaUtils;
import com.skopincev.videochangingdemoapp.media_processing.OnPlaybackStateChangeListener;
import com.skopincev.videochangingdemoapp.media_processing.OnResultListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity implements OnPlaybackStateChangeListener {

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
    private native void onPositionChanged(double position);
    private native void onStopPlaying();
    private native double getAudioPlayerDuration();
    private native double getAudioPlayerProgress();
    private native void saveChangedAudio(String inputFile, String outputFile, int cents);

    private SeekBar sbPitchShift;
    private TextView tvSpeed;
    private TextView tvCents;
    private FrameLayout flVideo;
    private VideoContentView videoView;
    private ProgressBar pbExtracting;
    private ProgressBar pbSaving;
    private RelativeLayout rlLayoutContainer;
    private Menu menu;

    private String samplerateString = null;
    private String buffersizeString = null;
    private File currentVideoFile = null;
    private File currentAudioFile = null;
    private String currentVideoFileName = null;
    private String selectedVideoFilePath = null;
    private FFmpeg ffmpeg = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAllPermissions();
    }

    private void checkAllPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1);
            }
        } else {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean granted = true;
        if (requestCode == 1) {
            if (grantResults.length == 2) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        granted = false;
                    }
                }
                if (granted) {
                    init();
                } else {
                    finish();
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void init() {
        initPlayersPositionTracking();

        initSuperpowered();

        initFFMpeg();

        initUI();
    }

    private void initSuperpowered() {
        // Get the device's sample rate and buffer size to enable low-latency Android audio output, if available.
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        if (samplerateString == null) samplerateString = "44100";
        if (buffersizeString == null) buffersizeString = "512";
    }

    private void initFFMpeg() {
        ffmpeg = FFmpeg.getInstance(getApplicationContext());
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {

                @Override
                public void onStart() {}

                @Override
                public void onFailure() {}

                @Override
                public void onSuccess() {}

                @Override
                public void onFinish() {}
            });
        } catch (FFmpegNotSupportedException e) {
            Log.d(TAG, "initFFMpeg: Device doesn't support ffmpeg");
        }
    }

    private void chooseVideoFile(){
        setUiDefaultConfig();

        Intent pickIntent = new Intent(Intent.ACTION_PICK);
        pickIntent.setType("video/*");
        startActivityForResult(Intent.createChooser(pickIntent,"Select Video"), REQUEST_TAKE_GALLERY_VIDEO);
    }

    private void setUiDefaultConfig() {
        sbPitchShift.setProgress(BundleConst.INIT_CENTS);
        setExtractingMode(false);
        videoView.pause();
    }

    private void cleanFilesDirectory() {
        File filesDir = getExternalFilesDir(null);
        if (filesDir.isDirectory())
        {
            String[] children = filesDir.list();
            for (int i = 0; i < children.length; i++)
            {
                new File(filesDir, children[i]).delete();
            }
        }
    }

    private void deleteSameFile(String fileName) {
        Log.d(TAG, "deleteSameFile: Same file " + (new File(fileName).delete() ? "deleted" : "is not exist"));
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                Uri selectedVideoUri = data.getData();

                String selectedVideoPath = getRealPathFromURI(selectedVideoUri);
                if (selectedVideoPath != null) {
                    selectedVideoFilePath = selectedVideoPath;
                    videoView.clear();

                    String[] temp = selectedVideoPath.split("/");
                    String videoFileName = temp[temp.length - 1].split("\\.")[0];
                    String videoFileFormat = temp[temp.length - 1].split("\\.")[1];
                    currentVideoFileName = videoFileName;

                    //Extracting audio
                    setExtractingMode(true);

                    String extractedAudioFileName = videoFileName + "_novideo";
                    String extractedAudioFilePath = getExternalFilesDir(null).getAbsolutePath() + "/" + extractedAudioFileName + ".aac";
                    //Delete file with same name
                    deleteSameFile(extractedAudioFilePath);
                    extractAudio(selectedVideoPath, extractedAudioFilePath);

                    currentAudioFile = new File(extractedAudioFilePath);
                    if (currentAudioFile.exists()){
                        //Configuring Superpowered audio player
                        int audioFileOffset = 0, audioFileLength = (int)currentAudioFile.length();
                        initAudioPlayer(Integer.parseInt(samplerateString), Integer.parseInt(buffersizeString), extractedAudioFilePath, audioFileOffset, audioFileLength);

                        //Extracting video
                        String extractedVideoFileName = videoFileName + "_nosound";
                        String extractedVideoFilePath = getExternalFilesDir(null).getAbsolutePath() + "/" + extractedVideoFileName + "." + videoFileFormat;
                        //Delete file with same name
                        deleteSameFile(extractedVideoFilePath);
                        extractVideo(selectedVideoPath, extractedVideoFilePath);
                    }
                } else {
                    Toast.makeText(this, "Can't open file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void setExtractingMode(boolean extracting) {
        if (extracting){
            pbExtracting.setVisibility(View.VISIBLE);
        } else {
            pbExtracting.setVisibility(View.INVISIBLE);
        }
        pbExtracting.setEnabled(extracting);
        menu.setGroupVisible(0, !extracting);
        menu.setGroupEnabled(0, !extracting);
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DATA);
            result = cursor.getString(columnIndex);
            cursor.close();
        }
        return result;
    }

    private void extractAudio(String videoFilePath, String extractedAudioFilePath){
        AudioExtractor audioExtractor = new AudioExtractor();
        audioExtractor.extract(videoFilePath, extractedAudioFilePath);
    }

    private void extractVideo(final String videoFilePath, final String extractedVideoFilePath){
        String[] commands = {"-i", videoFilePath, "-vcodec", "copy", "-an", extractedVideoFilePath};
        try {
            ffmpeg.execute(commands, new ExecuteBinaryResponseHandler(){
                @Override
                public void onStart() {
                    super.onStart();
                    Log.d(TAG, "extractVideo: Video extracting STARTED\n");
                }

                @Override
                public void onFinish() {
                    super.onFinish();
                    Log.d(TAG, "extractVideo: Video extracting FINISHED\n");
                    setExtractingMode(false);
                }

                @Override
                public void onSuccess(String message) {
                    super.onSuccess(message);
                    Log.d(TAG, "extractVideo: Video extracting SUCCEED\n" + message);
                    //Configuring video player
                    currentVideoFile = new File(extractedVideoFilePath);
                    if (currentVideoFile.exists()){
                        //Create new video view
                        flVideo.removeView(videoView);
                        videoView = new VideoContentView(MainActivity.this);
                        flVideo.addView(videoView);

                        errorEliminated = false;
                        videoView.initMediaPlayer(extractedVideoFilePath, MainActivity.this);
                    }
                }

                @Override
                public void onFailure(String message) {
                    super.onFailure(message);
                    Log.d(TAG, "extractVideo: Video extracting FAILED\n" + message);
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.d(TAG, "extractVideo: Video extracting FAILED" + e.getMessage());
        }
    }

    private void initUI() {
        rlLayoutContainer = findViewById(R.id.rl_layout_container);

        tvCents = findViewById(R.id.tv_cents);
        tvSpeed = findViewById(R.id.tv_speed);

        sbPitchShift = findViewById(R.id.sb_pitch_shift);
        sbPitchShift.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int cents = progress - BundleConst.INIT_CENTS;
                onCentsChanged(cents);
                tvCents.setText("cents: " + cents);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                videoView.restart();
                onPositionChanged(0);
            }
        });

        flVideo = findViewById(R.id.fl_video_container);
        videoView = new VideoContentView(this);
        flVideo.addView(videoView);

        pbExtracting = videoView.findViewById(R.id.pb_extracting);
        pbSaving = findViewById(R.id.pb_saving);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.mi_choose_video:{
                chooseVideoFile();
                return true;
            }
            case R.id.mi_save_video:{
                if (currentVideoFile != null && currentVideoFile.exists() &&
                        currentAudioFile != null && currentAudioFile.exists()){
                    String currentVideoFilePath = currentVideoFile.getAbsolutePath();
                    String currentAudioFilePath = currentAudioFile.getAbsolutePath();
                    int cents = sbPitchShift.getProgress() - BundleConst.INIT_CENTS;
                    String resultFilePath = getExternalFilesDir(null) + "/" +
                            currentVideoFileName + String.format("_cents(%d)", cents) + ".mp4";
                    deleteSameFile(resultFilePath);
                    saveVideo(currentVideoFilePath, currentAudioFilePath, resultFilePath);
                }
                return true;
            }
            default: {
                return false;
            }
        }
    }

    private void saveVideo(final String videoFilePath, final String audioFilePath, final String resultFilePath){
        if (!isNoEffects()) {
            //Set saving mode
            setSavingMode(true);

            //Save pitch shifted audio
            final String resultAudioFilePath_Wave = getExternalFilesDir(null) + "/resultAudio_wave.wav";
            deleteSameFile(resultAudioFilePath_Wave);
            int cents = sbPitchShift.getProgress() - BundleConst.INIT_CENTS;
            saveChangedAudio(audioFilePath, resultAudioFilePath_Wave, cents);

            //Convert pitch shifted audio file from WAVE into AAC audio file
            final String resultAudioFilePath = getExternalFilesDir(null) + "/resultAudio.aac";
            deleteSameFile(resultAudioFilePath);
            AACEncoder encoder = new AACEncoder();
            encoder.convertToAAC(resultAudioFilePath_Wave, resultAudioFilePath, new OnResultListener() {
                @Override
                public void onOperationFinished(int resultCode) {
                    if (resultCode == SUCCESS) {
                        //Merge pitch shifted audio and video
                        MediaMerger merger = new MediaMerger();
                        merger.mergeWithMuxer(resultAudioFilePath, videoFilePath, resultFilePath);
                        Log.d(TAG, "Video saving SUCCEED");

                        //Put video into Gallery
                        addVideoToGallery(new File(resultFilePath));

                        Log.d(TAG, String.format("Extracted audio bitRate: %d, sampleRate: %d, durationMs: %.2f",
                                MediaUtils.getAudioFileBitRate(audioFilePath),
                                MediaUtils.getAudioFileSampleRate(audioFilePath),
                                MediaUtils.getAudioFileDurationMs(audioFilePath)));
                        Log.d(TAG, String.format("Time stretched audio bitRate: %d, sampleRate: %d, durationMs: %.2f",
                                MediaUtils.getAudioFileBitRate(resultAudioFilePath_Wave),
                                MediaUtils.getAudioFileSampleRate(resultAudioFilePath_Wave),
                                MediaUtils.getAudioFileDurationMs(resultAudioFilePath_Wave)));
                        Log.d(TAG, String.format("Encoded audio bitRate: %d, sampleRate: %d, durationMs: %.2f",
                                MediaUtils.getAudioFileBitRate(resultAudioFilePath),
                                MediaUtils.getAudioFileSampleRate(resultAudioFilePath),
                                MediaUtils.getAudioFileDurationMs(resultAudioFilePath)));

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Video saved", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        Log.d(TAG, "Video saving FAILED");
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setSavingMode(false);
                        }
                    });
                }
            });
        } else {
            //Set saving mode
            setSavingMode(true);

            try {
                String appDirectoryName = getApplicationName(this) + " Videos";
                File appFilesDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES), appDirectoryName);
                if (!appFilesDir.exists())
                    appFilesDir.mkdirs();
                int cents = sbPitchShift.getProgress() - BundleConst.INIT_CENTS;
                File video = new File(appFilesDir, currentVideoFileName + String.format("_cents(%d)", cents) + ".mp4");
                copyVideoToGallery(selectedVideoFilePath, video.getPath());
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(video)));

                Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e){
                e.printStackTrace();
                Toast.makeText(this, "Video saving failed", Toast.LENGTH_SHORT).show();
            }
            //Set saving mode
            setSavingMode(false);
        }
    }

    private boolean isNoEffects() {
        if (sbPitchShift.getProgress() - BundleConst.INIT_CENTS == 0){
            return true;
        } else {
            return false;
        }
    }

    private String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    private void copyVideoToGallery(String inputPath, String outputPath) {
        InputStream in;
        OutputStream out;
        try {

            in = new FileInputStream(inputPath);
            out = new FileOutputStream(outputPath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();

            // write the output file
            out.flush();
            out.close();

            Log.d(TAG, "copyVideoToGallery: Video file copying SUCCEED");
        } catch (Exception e) {
            Log.d(TAG, "copyVideoToGallery: Video file copying FAILED\n" + e.getMessage());
        }

    }

    public void addVideoToGallery(File videoFile) {
        String appDirectoryName = getApplicationName(this) + " Videos";
        File appFilesDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), appDirectoryName);
        if (!appFilesDir.exists())
            appFilesDir.mkdirs();
        int cents = sbPitchShift.getProgress() - BundleConst.INIT_CENTS;
        File video = new File(appFilesDir, currentVideoFileName + String.format("_cents(%d)", cents) + ".mp4");
        copyVideoToGallery(videoFile.getPath(), video.getPath());
        // Delete the original file from FS
        deleteSameFile(videoFile.getPath());
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(video)));
    }

    private void saveVideoWithTimeStretching(String videoFilePath, String resultFilePath, float speed) {
        float factor = 1 / speed;
        String[] commands =
                {       "-y",
                        "-i",
                        videoFilePath,
                        "-filter_complex",
                        "[0:v]fps=50.0, setpts=" + String.format("%f", factor) + "*PTS[v]",
                        "-map",
                        "[v]",
                        "-preset",
                        "ultrafast",
                        resultFilePath
                };
        try {
            ffmpeg.execute(commands, new ExecuteBinaryResponseHandler(){
                @Override
                public void onStart() {
                    super.onStart();
                    Log.d(TAG, "saveVideoWithTimeStretching: Video saving STARTED\n");
                    setSavingMode(true);
                }

                @Override
                public void onFinish() {
                    super.onFinish();
                    Log.d(TAG, "saveVideoWithTimeStretching: Video saving FINISHED\n");
                    setSavingMode(false);
                }

                @Override
                public void onProgress(String message) {
                    super.onProgress(message);
                    Log.d(TAG, "saveVideoWithTimeStretching: Video saving progress:\n" + message);
                }

                @Override
                public void onSuccess(String message) {
                    super.onSuccess(message);
                    Log.d(TAG, "saveVideoWithTimeStretching: Video saving SUCCEED\n" + message);
                }

                @Override
                public void onFailure(String message) {
                    super.onFailure(message);
                    Log.d(TAG, "saveVideoWithTimeStretching: Video saving FAILED\n" + message);
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.d(TAG, "saveVideoWithTimeStretching: Video saving FAILED" + e.getMessage());
        }
    }

    private void setSavingMode(boolean saving) {
        if (saving){
            videoView.pause();
            videoView.hideMediaControls();
            rlLayoutContainer.setVisibility(View.INVISIBLE);
            pbSaving.setVisibility(View.VISIBLE);
            Log.d(TAG, "Video saving STARTED");
        } else {
            rlLayoutContainer.setVisibility(View.VISIBLE);
            pbSaving.setVisibility(View.INVISIBLE);
            Log.d(TAG, "Video saving FINISHED");
        }
        pbSaving.setEnabled(saving);
        menu.setGroupVisible(0, !saving);
        menu.setGroupEnabled(0, !saving);
    }

    private boolean tracking = false;
    private double lastAudioTrackPosition;
    private double lastVideoTrackPosition;
    private int timeToSleep = 2000;
    private boolean errorEliminated = false;
    private void initPlayersPositionTracking(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!isDestroyed()){
                    while (tracking){
                        if (videoView != null) {

                            double videoTrackDuration = videoView.getDuration();
                            double videoTrackPosition = videoView.getCurrentPosition();

                            double audioPlayerDuration = getAudioPlayerDuration();
                            double audioTrackPosition = getAudioPlayerProgress();

                            double diff = audioTrackPosition - videoTrackPosition;

                            Log.d(TAG, "logPlayersData: Diff between audio track positions = "
                                    + String.format("%.2f", lastAudioTrackPosition - audioTrackPosition + timeToSleep)
                                    + "; Position: " + String.format("%.2f", audioTrackPosition));
                            Log.d(TAG, "logPlayersData: Diff between video track positions = "
                                    + String.format("%.2f", lastVideoTrackPosition - videoTrackPosition + timeToSleep)
                                    + "; Position: " + String.format("%.2f", videoTrackPosition));
                            Log.d(TAG, "logPlayersData: Diff between audio and video positions = "
                                    + String.format("%.2f", diff));
                            Log.d(TAG, "logPlayersData: Audio track longer for "
                                    + String.format("%.2f", audioPlayerDuration - videoTrackDuration));

                            lastVideoTrackPosition = videoTrackPosition;
                            lastAudioTrackPosition = audioTrackPosition;

                            reduceVideoError(diff, videoTrackPosition);

                            try {
                                Thread.sleep(timeToSleep);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }).start();
    }

    private void reduceVideoError(double diff, double videoTrackPosition) {
        if (diff > 0f && !errorEliminated){
            videoView.seekTo((int)(videoTrackPosition + diff));
            errorEliminated = true;
        }
    }

    @Override
    public void setPlayState(boolean play) {
        onPlayPause(play);
        tracking = play;
    }

    @Override
    public void setNewPositionState(double position) {
        onPositionChanged(position);
    }

    @Override
    public void setStopState() {
        onStopPlaying();
        lastVideoTrackPosition = 0;
        lastAudioTrackPosition = 0;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null)
            videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null)
            videoView.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanFilesDirectory();
        tracking = false;
    }
}
