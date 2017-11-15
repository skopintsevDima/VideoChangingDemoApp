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
        deleteAllWorkingFiles();
        super.onTerminate();
    }

    private void deleteAllWorkingFiles(){
        File filesDir = new File(getFilesDir().getParent());
        if (filesDir.isDirectory()) {
            String[] children = filesDir.list();
            for (int i = 0; i < children.length; i++) {
                new File(filesDir, children[i]).delete();
            }
        }
    }
}
