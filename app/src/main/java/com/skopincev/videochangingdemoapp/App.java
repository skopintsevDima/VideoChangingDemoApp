package com.skopincev.videochangingdemoapp;

import android.app.Application;

import java.io.File;

/**
 * Created by skopi on 15.11.2017.
 */

public class App extends Application {
    @Override
    public void onTerminate() {
        // TODO: 15.11.2017 delete all from files/ dir
        // TODO: 21.11.2017 clean code from comments
        super.onTerminate();
    }
}
