package com.skopincev.videochangingdemoapp.ui;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.MediaController;
import android.widget.RelativeLayout;

/**
 * Created by Stormtrooper on 23.04.2017.
 */

public class VideoController extends MediaController {

    private final static String TAG = "VideoController";
    private View anchorView;

    public VideoController(Context context, View anchorView) {
        super(context);
        this.anchorView = anchorView;
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld) {
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        Log.d(TAG, "onSizeChanged, xNew=" + xNew + ", yNew=" + yNew + ", xOld=" + xOld + ", yOld=" + yOld);


        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.JELLY_BEAN_MR1){
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) anchorView.getLayoutParams();
            lp.setMargins(0, 0, 0, yNew);

            anchorView.setLayoutParams(lp);
            anchorView.requestLayout();
            Log.d(TAG, "bottomMargin = " + ((RelativeLayout.LayoutParams) anchorView.getLayoutParams()).bottomMargin);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        // don't hide controller when BACK key pressed
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            ((Activity)getContext()).finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
