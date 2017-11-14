package com.skopincev.videochangingdemoapp.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.skopincev.videochangingdemoapp.R;
import com.skopincev.videochangingdemoapp.media_processing.OnPlaybackStateChangeListener;

import java.io.IOException;

/**
 * Created by skopi on 02.06.2017.
 */

public class VideoContentView extends RelativeLayout implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaController.MediaPlayerControl,
        MediaPlayer.OnCompletionListener,
        TextureView.SurfaceTextureListener{

    private static final String TAG = VideoContentView.class.getSimpleName();

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    private MediaPlayer mediaPlayer = null;
    private RelativeLayout relativeLayout;
    private MediaController mediaController;
    private View controllerAnchor;
    private SeekBar seekbar;
    private int bufferPercentage;
    private int currentPosition;
    private int videoDuration;
    private TextureView textureView;
    private OnPlaybackStateChangeListener playbackStateChangeListener;

    public VideoContentView(Context context) {
        super(context);
        init();
    }

    public VideoContentView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoContentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public VideoContentView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.view_video_content, this);
    }

    public void initMediaPlayer(String videoPath, OnPlaybackStateChangeListener playbackStateChangeListener) {
        this.playbackStateChangeListener = playbackStateChangeListener;
        textureView = this.findViewById(R.id.txv_video_surface_view);
        textureView.setSurfaceTextureListener(this);
        relativeLayout = this.findViewById(R.id.rl_video_layout);
        relativeLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showMediaControls();
            }
        });
        controllerAnchor = this.findViewById(R.id.fl_video_controller_anchor);
        mediaController = new VideoController(this.getContext(), controllerAnchor);

        releaseMediaPlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(videoPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaPlayer.setScreenOnWhilePlaying(true);
//        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnCompletionListener(this);

        mediaPlayer.prepareAsync();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showMediaControls() {
        if (mediaController == null) {
            return;
        }
        if (mediaController.isShowing()) {
            mediaController.hide();
        } else {
            mediaController.show();
        }
    }

    /**
     * Calc size of the View Layout depending on the video size
     */
    private void handleAspectRatio(MediaPlayer mediaPlayer) {

        if (textureView == null) {
            textureView = ((Activity) getContext()).findViewById(R.id.txv_video_surface_view);
        }
        //Get the dimensions of the surfaceView
        float surfaceViewWidth = textureView.getWidth(); //1044
        int surfaceViewHeight = textureView.getHeight(); //768
        Log.d(TAG, "surfaceViewWidth " + surfaceViewWidth);
        Log.d(TAG, "surfaceViewHeight " + surfaceViewHeight);

        //Get the dimensions of the video
        float videoWidth = mediaPlayer.getVideoWidth(); //
        float videoHeight = mediaPlayer.getVideoHeight(); //
        Log.d(TAG, "videoWidth" + videoWidth);
        Log.d(TAG, "videoHeight" + videoHeight);

        //Get the proportion of video
        double aspectRatio = (double) videoHeight / videoWidth;

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) textureView.getLayoutParams();

        if (surfaceViewWidth <= videoWidth) {
            //Make video width match parent to screen
            //Log.d(TAG, "surfaceView width is smaller than video width");
            layoutParams.width = (int) (surfaceViewWidth);
            layoutParams.height = (int) (surfaceViewWidth * aspectRatio);
        } else {
            //Make the width of video match parent
            //Log.d(TAG, "screenDpWidth>videoWidt");
            layoutParams.width = (int) (surfaceViewHeight / aspectRatio);
            layoutParams.height = surfaceViewHeight;
        }

        Log.v(TAG, "layoutParams.width" + layoutParams.width);
        Log.v(TAG, "layoutParams.height" + layoutParams.height);

        relativeLayout.setLayoutParams(layoutParams);
        textureView.setLayoutParams(layoutParams);
    }

    /**MediaPlayer.OnPreparedListener interface method */
    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        handleAspectRatio(mediaPlayer);

        if (seekbar == null) {
            int topContainerId = getResources().getIdentifier("mediacontroller_progress", "id", "android");
            seekbar = mediaController.findViewById(topContainerId);
        }

        if (mediaController != null) {
            mediaController.setMediaPlayer(this);
            mediaController.setAnchorView(controllerAnchor);
            mediaController.setEnabled(true);
        }

        mediaPlayer.seekTo(currentPosition);
        if (textureView.isAvailable()) {
            onSurfaceTextureAvailable(textureView.getSurfaceTexture(), textureView.getWidth(), textureView.getHeight());
        }
        videoDuration = mediaPlayer.getDuration();

        showMediaControls();

        Log.d(TAG, "onPrepared: Player prepared");
    }

    public void clear(){
        if (mediaController != null) {
            mediaController.hide();
            mediaController.removeAllViews();
            mediaController = null;
        }
        releaseMediaPlayer();
        currentPosition = 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public void start() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
            playbackStateChangeListener.setPlayState(true);
        }
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public void pause() {
        Log.d(TAG, "pause() ");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playbackStateChangeListener.setPlayState(false);
        }
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public int getDuration() {
        Log.d(TAG, "getDuration: Video duration = " + videoDuration);
        return videoDuration;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                currentPosition = mediaPlayer.getCurrentPosition();
                return currentPosition;
            } catch (IllegalStateException ignore) {
                Log.e(TAG, "IllegalStateException on getCurrentPosition");
            }
        }
        return 0;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public void seekTo(int pos) {
        if (mediaPlayer != null) {
            playbackStateChangeListener.setNewPositionState((double)pos);
            mediaPlayer.seekTo(pos);
            Log.d("Playing position", "Video position: " + pos);
        }
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (IllegalStateException ignore) {
                Log.e(TAG, "IllegalStateException on isPlaying()");
            }
        }
        return false;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public int getBufferPercentage() {
        return bufferPercentage;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public boolean canPause() {
        return true;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public boolean canSeekBackward() {
        return true;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public boolean canSeekForward() {
        return true;
    }

    /** MediaController.MediaPlayerControl interface method */
    @Override
    public int getAudioSessionId() {
        if (mediaPlayer != null) {
            return mediaPlayer.getAudioSessionId();
        }
        return 0;
    }

    /**MediaPlayer.OnErrorListener interface method */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "what = " + what + ", extra = " + extra);
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "onCompletion");
        showMediaControls();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable: Surface created");
        if (mediaPlayer != null) {
            Surface surface = new Surface(surfaceTexture);
            mediaPlayer.setSurface(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(null);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
}
