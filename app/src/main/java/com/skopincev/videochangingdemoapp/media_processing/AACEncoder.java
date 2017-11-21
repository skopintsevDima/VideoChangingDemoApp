package com.skopincev.videochangingdemoapp.media_processing;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static com.skopincev.videochangingdemoapp.media_processing.OnResultListener.*;

/**
 * Created by skopi on 20.11.2017.
 */

public class AACEncoder {

    public static final String TAG = AACEncoder.class.getSimpleName();

    public static MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate,
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

    public static final String COMPRESSED_AUDIO_FILE_MIME_TYPE = "audio/mp4a-latm";
    public static final int COMPRESSED_AUDIO_FILE_BIT_RATE = 320000; // 320kbps
    public static final int SAMPLING_RATE = 48000;
    public static final int BUFFER_SIZE = 48000;
    public static final int CODEC_TIMEOUT_IN_MS = 5000;

    public void convertToAAC(final String inputFilePath, final String outputFilePath, final OnResultListener resultListener){

        Thread converter = new Thread(new Runnable() {
            @SuppressLint("WrongConstant")
            @Override
            public void run() {
                //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    File inputFile = new File(inputFilePath);
                    FileInputStream fis = new FileInputStream(inputFile);

                    File outputFile = new File(outputFilePath);
                    if (outputFile.exists()) outputFile.delete();

                    MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                    MediaFormat outputFormat = MediaFormat.createAudioFormat(COMPRESSED_AUDIO_FILE_MIME_TYPE,SAMPLING_RATE, 1);
                    outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                    outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, COMPRESSED_AUDIO_FILE_BIT_RATE);
                    outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

                    MediaCodec codec = MediaCodec.createEncoderByType(COMPRESSED_AUDIO_FILE_MIME_TYPE);
                    codec.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    codec.start();

                    ByteBuffer[] codecInputBuffers = codec.getInputBuffers(); // Note: Array of buffers
                    ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

                    MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
                    byte[] tempBuffer = new byte[BUFFER_SIZE];
                    boolean hasMoreData = true;
                    double presentationTimeUs = 0;
                    int audioTrackIdx = 0;
                    int totalBytesRead = 0;
                    int percentComplete = 0;
                    do {
                        int inputBufIndex = 0;
                        while (inputBufIndex != -1 && hasMoreData) {
                            inputBufIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_IN_MS);

                            if (inputBufIndex >= 0) {
                                ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                                dstBuf.clear();

                                int bytesRead = fis.read(tempBuffer, 0, dstBuf.limit());
                                Log.e("bytesRead","Readed "+bytesRead);
                                if (bytesRead == -1) { // -1 implies EOS
                                    hasMoreData = false;
                                    codec.queueInputBuffer(inputBufIndex, 0, 0, (long) presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                } else {
                                    totalBytesRead += bytesRead;
                                    dstBuf.put(tempBuffer, 0, bytesRead);
                                    codec.queueInputBuffer(inputBufIndex, 0, bytesRead, (long) presentationTimeUs, 0);
                                    presentationTimeUs = 1000000l * (totalBytesRead / 2) / SAMPLING_RATE;
                                }
                            }
                        }
                        // Drain audio
                        int outputBufIndex = 0;
                        while (outputBufIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                            outputBufIndex = codec.dequeueOutputBuffer(outBuffInfo, CODEC_TIMEOUT_IN_MS);
                            if (outputBufIndex >= 0) {
                                ByteBuffer encodedData = codecOutputBuffers[outputBufIndex];
                                encodedData.position(outBuffInfo.offset);
                                encodedData.limit(outBuffInfo.offset + outBuffInfo.size);
                                if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0 && outBuffInfo.size != 0) {
                                    codec.releaseOutputBuffer(outputBufIndex, false);
                                }else{
                                    muxer.writeSampleData(audioTrackIdx, codecOutputBuffers[outputBufIndex], outBuffInfo);
                                    codec.releaseOutputBuffer(outputBufIndex, false);
                                }
                            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                outputFormat = codec.getOutputFormat();
                                Log.v(TAG, "Output format changed - " + outputFormat);
                                audioTrackIdx = muxer.addTrack(outputFormat);
                                muxer.start();
                            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                Log.e(TAG, "Output buffers changed during encode!");
                            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                // NO OP
                            } else {
                                Log.e(TAG, "Unknown return code from dequeueOutputBuffer - " + outputBufIndex);
                            }
                        }
                        percentComplete = (int) Math.round(((float) totalBytesRead / (float) inputFile.length()) * 100.0);
                        Log.v(TAG, "Conversion % - " + percentComplete);
                    } while (outBuffInfo.flags != MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    fis.close();
                    muxer.stop();
                    muxer.release();
                    Log.v(TAG, "Compression done ...");
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "File not found!", e);
                    resultListener.onOperationFinished(FAILED);
                } catch (IOException e) {
                    Log.e(TAG, "IO exception!", e);
                    resultListener.onOperationFinished(FAILED);
                }

                //mStop = false;
                resultListener.onOperationFinished(SUCCESS);
            }
        });

        converter.start();
    }

    public void encodeWaveToAac(FFmpeg ffmpeg,
                                final String inputFilePath, final String outputFilePath,
                                final OnResultListener resultListener){
        String[] commands = {
                "-i",
                inputFilePath,
                "-c:a",
                "aac",
                "-q:a",
                "2",
                outputFilePath
        };
        try {
            ffmpeg.execute(commands, new ExecuteBinaryResponseHandler(){
                @Override
                public void onStart() {
                    super.onStart();
                    Log.d(TAG, "encodeWaveToAac: Audio converting STARTED\n");
                }

                @Override
                public void onFinish() {
                    super.onFinish();
                    Log.d(TAG, "encodeWaveToAac: Audio converting FINISHED\n");
                }

                @Override
                public void onProgress(String message) {
                    super.onProgress(message);
                    Log.d(TAG, "encodeWaveToAac: Audio converting progress:\n" + message);
                }

                @Override
                public void onSuccess(String message) {
                    super.onSuccess(message);
                    Log.d(TAG, "encodeWaveToAac: Audio converting SUCCEED\n" + message);
                    resultListener.onOperationFinished(SUCCESS);
                }

                @Override
                public void onFailure(String message) {
                    super.onFailure(message);
                    Log.d(TAG, "encodeWaveToAac: Audio converting FAILED\n" + message);
                    resultListener.onOperationFinished(FAILED);
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            Log.d(TAG, "encodeWaveToAac: Audio converting FAILED" + e.getMessage());
        }
    }

}
