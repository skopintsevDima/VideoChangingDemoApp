package com.skopincev.videochangingdemoapp.media_processing;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by skopi on 12.11.2017.
 */

public class AudioExtractor {

    private static final String TAG = AudioExtractor.class.getSimpleName();
    private static final String AUDIO = "audio/";

    public AudioExtractor(){

    }

    private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate,
                                                 int channelConfig)
    {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig);

        int samplingFreq[] = {
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000
        };

        // Search the Sampling Frequencies
        int sampleIndex = -1;
        for (int i = 0; i < samplingFreq.length; ++i)
        {
            if (samplingFreq[i] == sampleRate)
            {
                sampleIndex = i;
            }
        }

        if (sampleIndex == -1)
        {
            return null;
        }

        ByteBuffer csd = ByteBuffer.allocate(2);
        csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));

        csd.position(1);
        csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
        csd.flip();
        format.setByteBuffer("csd-0", csd); // add csd-0

        for (int k = 0; k < csd.capacity(); ++k)
        {
            Log.e(TAG, "csd : " + csd.array()[k]);
        }

        return format;
    }

    public void extract(String inputFile, String outputFile) {
        try {

            File file = new File(outputFile);
            file.createNewFile();

            MediaExtractor audioExtractor = new MediaExtractor();
            FileInputStream fileInputStream = new FileInputStream(inputFile);
            FileDescriptor fileDescriptor = fileInputStream.getFD();
            audioExtractor.setDataSource(fileDescriptor);
            fileInputStream.close();

            MediaMuxer muxer = null;
            int channel = 0;
            int mSampleRate = 0;
            for (int i = 0; i < audioExtractor.getTrackCount(); i++)
            {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(AUDIO))
                {
                    audioExtractor.selectTrack(i);
                    Log.d(TAG, "format : " + format);

                    mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channel = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    break;
                }
            }
            MediaFormat audioFormat = makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                    mSampleRate, channel);
            muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int audioTrack = muxer.addTrack(audioFormat);

            boolean sawEOS = false;
            int offset = 100;
            int sampleSize = 256 * 1024;
            ByteBuffer audioBuffer = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            muxer.start();

            while (!sawEOS) {
                audioBufferInfo.offset = offset;
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuffer, offset);

                if (audioBufferInfo.size < 0) {
                    Log.d(TAG, "saw input EOS.");
                    sawEOS = true;
                    audioBufferInfo.size = 0;

                } else {
                    audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    audioBufferInfo.flags = audioExtractor.getSampleFlags();
                    muxer.writeSampleData(audioTrack, audioBuffer, audioBufferInfo);
                    audioExtractor.advance();

                }
            }

            audioExtractor.release();

            muxer.stop();
            muxer.release();

        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
    }
}
