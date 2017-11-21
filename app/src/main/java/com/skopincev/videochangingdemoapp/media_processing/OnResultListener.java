package com.skopincev.videochangingdemoapp.media_processing;

/**
 * Created by skopi on 21.11.2017.
 */

public interface OnResultListener {
    int SUCCESS = 1;
    int FAILED = 2;

    void onOperationFinished(int resultCode);
}
