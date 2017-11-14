package com.skopincev.videochangingdemoapp.media_processing;

/**
 * Created by skopi on 14.11.2017.
 */

public interface OnPlaybackStateChangeListener {
    void setPlayState(boolean play);
    void setNewPositionState(double msec);
}
