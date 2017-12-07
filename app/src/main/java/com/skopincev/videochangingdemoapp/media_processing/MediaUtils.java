package com.skopincev.videochangingdemoapp.media_processing;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.skopincev.videochangingdemoapp.BundleConst;

import java.io.File;

/**
 * Created by skopi on 04.12.2017.
 */

public class MediaUtils {
    public static int getAudioFileBitRate(String filePath){
        double bitRate = 0;
        MediaExtractor extractor = new MediaExtractor();
        MediaFormat audioFormat;
        try {
            //Calculate bitrate
            extractor.setDataSource(filePath);
            audioFormat = extractor.getTrackFormat(0);
            double duration = (double)audioFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;
            double size = new File(filePath).length() * 8;
            bitRate = size / duration;

            //Correlation of bitrate
            int index = 0;
            double diff = Double.MAX_VALUE;
            for (int i = 0; i < BundleConst.samplingFrequencies.length; i++){
                double currentDiff = Math.abs(bitRate - BundleConst.samplingFrequencies[i]);
                if (currentDiff < diff){
                    diff = currentDiff;
                    index = i;
                }
            }
            bitRate = BundleConst.samplingFrequencies[index];
        } catch (Exception e) {
            e.printStackTrace();
        }

        return (int)bitRate;
    }

    public static int getAudioFileSampleRate(String filePath){
        int sampleRate = 0;
        MediaExtractor extractor = new MediaExtractor();
        MediaFormat audioFormat;
        try {
            //Calculate sample rate
            extractor.setDataSource(filePath);
            audioFormat = extractor.getTrackFormat(0);
            int channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) * channelCount;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sampleRate;
    }

    public static double getAudioFileDurationMs(String filePath){
        double durationMs = 0;
        MediaExtractor extractor = new MediaExtractor();
        MediaFormat audioFormat;
        try {
            //Calculate duration
            extractor.setDataSource(filePath);
            audioFormat = extractor.getTrackFormat(0);
            durationMs = (double) audioFormat.getLong(MediaFormat.KEY_DURATION) / 1000;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return durationMs;
    }
}
