//
// Created by skopi on 07.11.2017.
//

#include "AudioPlayer.h"
#include <SuperpoweredSimple.h>
#include <SuperpoweredCPU.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>
#include <string.h>
#include <android/log.h>
#include <pthread.h>
#include "SuperpoweredDecoder.h"
#include "SuperpoweredRecorder.h"

static const char *TAG = "AudioProcessing";

static JNIEnv *javaEnvironment;

static void playerEventCallback(void *clientData, SuperpoweredAdvancedAudioPlayerEvent event, void * __unused value) {
    if (event == SuperpoweredAdvancedAudioPlayerEvent_LoadSuccess) {
        SuperpoweredAdvancedAudioPlayer *player = *((SuperpoweredAdvancedAudioPlayer **)clientData);
        player->setBpm(126.0f);
        player->setFirstBeatMs(353);
        player->setPosition(player->firstBeatMs, false, false);
    };
}

static bool audioProcessing(void *clientdata, short int *audioIO, int numberOfSamples, int __unused samplerate) {
    return ((NDKAudioPlayer *)clientdata)->process(audioIO, (unsigned int)numberOfSamples);
}

NDKAudioPlayer::NDKAudioPlayer(unsigned int samplerate, unsigned int buffersize, const char *path,
                               int audioFileOffset, int audioFileLength): volume(1.5f) { //* headroom) {
    stereoBuffer = (float *)memalign(16, (buffersize + 16) * sizeof(float) * 2);

    player = new SuperpoweredAdvancedAudioPlayer(&player , playerEventCallback, samplerate, 0);
    player->open(path, audioFileOffset, audioFileLength);
    player->syncMode = SuperpoweredAdvancedAudioPlayerSyncMode_TempoAndBeat;

    audioSystem = new SuperpoweredAndroidAudioIO(samplerate, buffersize, false, true, audioProcessing, this, -1, SL_ANDROID_STREAM_MEDIA, buffersize * 2);
}

NDKAudioPlayer::~NDKAudioPlayer() {
    delete audioSystem;
    delete player;
    free(stereoBuffer);
}

void NDKAudioPlayer::onPlayPause(bool play) {
    if (!play) {
        player->pause();
    } else {
        player->play(false);
    };
    SuperpoweredCPU::setSustainedPerformanceMode(play); // <-- Important to prevent audio dropouts.
}

bool NDKAudioPlayer::process(short *output, unsigned int numberOfSamples) {
    double masterBpm = player->currentBpm;

    bool silence = !player->process(stereoBuffer, false, numberOfSamples, volume, masterBpm);

    // The stereoBuffer is ready now, let's put the finished audio into the requested buffers.
    if (!silence)
        SuperpoweredFloatToShortInt(stereoBuffer, output, numberOfSamples);
    return !silence;
}

void NDKAudioPlayer::onCentsChanged(int cents) {
    player->setPitchShiftCents(cents);
}

void NDKAudioPlayer::onTempoChanged(double tempo) {
    player->setTempo(tempo, true);
}

void NDKAudioPlayer::onPositionChanged(double percentage) {
    player->seek(percentage);
    char msecString[64];
    snprintf(msecString, sizeof(msecString), "%g", player->positionMs / player->durationMs);
    char message[64] = "Audio player position: ";
    strcat(message, msecString);
    __android_log_write(ANDROID_LOG_DEBUG, "AudioPP", message);
}

void NDKAudioPlayer::onStop() {
    player->pause();
    player->seek(0);
}

double NDKAudioPlayer::getProgress() {
    return player->positionMs / player->durationMs;
}

static void saveTimeStretchedAudio(const char *inputPath, const char *outputPath, int cents) {
    // Open the input file.
    SuperpoweredDecoder *decoder = new SuperpoweredDecoder();
    const char *openError = decoder->open(inputPath, false, 0, 0);
    if (openError) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Input file name: %s", inputPath);
        delete decoder;
        return;
    };

    // Create the output WAVE file.
    FILE *fd = createWAV(outputPath, decoder->samplerate, 2);
    if (!fd) {
        __android_log_write(ANDROID_LOG_DEBUG, TAG, "File not created");
        delete decoder;
        return;
    };

    /*
     Due to it's nature, a time stretcher can not operate with fixed buffer sizes.
     This problem can be solved with variable size buffer chains (complex) or FIFO buffering (easier).

     Memory bandwidth on mobile devices is way lower than on desktop (laptop), so we need to use variable size buffer chains here.
     This solution provides almost 2x performance increase over FIFO buffering!
    */
    SuperpoweredTimeStretching *timeStretcher = new SuperpoweredTimeStretching(decoder->samplerate);
    timeStretcher->setRateAndPitchShiftCents(1.0f, cents);
    // This buffer list will receive the time-stretched samples.
    SuperpoweredAudiopointerList *outputBuffers = new SuperpoweredAudiopointerList(8, 16);

    // Create a buffer for the 16-bit integer samples.
    short int *intBuffer = (short int *)malloc(decoder->samplesPerFrame * 2 * sizeof(short int) + 32768);

    // Processing.
    while (true) {
        // Decode one frame. samplesDecoded will be overwritten with the actual decoded number of samples.
        unsigned int samplesDecoded = decoder->samplesPerFrame;
        if (decoder->decode(intBuffer, &samplesDecoded) == SUPERPOWEREDDECODER_ERROR) break;
        if (samplesDecoded < 1) break;

        // Create an input buffer for the time stretcher.
        SuperpoweredAudiobufferlistElement inputBuffer;
        inputBuffer.samplePosition = decoder->samplePosition;
        inputBuffer.startSample = 0;
        inputBuffer.samplesUsed = 0;
        inputBuffer.endSample = samplesDecoded; // <-- Important!
        inputBuffer.buffers[0] = SuperpoweredAudiobufferPool::getBuffer(samplesDecoded * 8 + 64);
        inputBuffer.buffers[1] = inputBuffer.buffers[2] = inputBuffer.buffers[3] = NULL;

        // Convert the decoded PCM samples from 16-bit integer to 32-bit floating point.
        SuperpoweredShortIntToFloat(intBuffer, (float *)inputBuffer.buffers[0], samplesDecoded);

        // Time stretching.
        timeStretcher->process(&inputBuffer, outputBuffers);

        // Do we have some output?
        if (outputBuffers->makeSlice(0, outputBuffers->sampleLength)) {

            while (true) { // Iterate on every output slice.
                // Get pointer to the output samples.
                int numSamples = 0;
                float *timeStretchedAudio = (float *)outputBuffers->nextSliceItem(&numSamples);
                if (!timeStretchedAudio) break;

                // Convert the time stretched PCM samples from 32-bit floating point to 16-bit integer.
                SuperpoweredFloatToShortInt(timeStretchedAudio, intBuffer, numSamples);

                // Write the audio to disk.
                fwrite(intBuffer, 1, numSamples * 4, fd);
            };

            // Clear the output buffer list.
            outputBuffers->clear();
        };
    };

    // Cleanup.
    closeWAV(fd);
    delete decoder;
    delete timeStretcher;
    delete outputBuffers;
    free(intBuffer);
}

static NDKAudioPlayer *audioPlayer;

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_initAudioPlayer(JNIEnv *jniEnv, jobject __unused obj, jint samplerate, jint buffersize, jstring audioFilePath, jint audioFileOffset, jint audioFileLength) {
    const char *path = jniEnv->GetStringUTFChars(audioFilePath, JNI_FALSE);
    audioPlayer = new NDKAudioPlayer((unsigned int)samplerate, (unsigned int)buffersize, path, audioFileOffset, audioFileLength);
    jniEnv->ReleaseStringUTFChars(audioFilePath, path);

    javaEnvironment = jniEnv;
}

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_onPlayPause(JNIEnv *jniEnv, jobject instance, jboolean play) {
    if (audioPlayer != NULL)
        audioPlayer->onPlayPause(play);
}

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_onCentsChanged(JNIEnv *jniEnv, jobject instance, jint cents) {
    if (audioPlayer != NULL)
        audioPlayer->onCentsChanged((int)cents);
}

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_onTempoChanged(JNIEnv *jniEnv, jobject instance, jdouble tempo) {
    if (audioPlayer != NULL)
        audioPlayer->onTempoChanged((double)tempo);
}

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_onPositionChanged(JNIEnv *jniEnv, jobject instance, jdouble percentage) {
    if (audioPlayer != NULL)
        audioPlayer->onPositionChanged((double)percentage);
}

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_onStopPlaying(JNIEnv *jniEnv, jobject instance) {
    if (audioPlayer != NULL)
        audioPlayer->onStop();
}

extern "C" JNIEXPORT jdouble Java_com_skopincev_videochangingdemoapp_ui_MainActivity_getAudioPlayerProgress(JNIEnv *jniEnv, jobject instance) {
    if (audioPlayer != NULL)
        return audioPlayer->getProgress();
    else
        return 0;
}

extern "C" JNIEXPORT void Java_com_skopincev_videochangingdemoapp_ui_MainActivity_saveChangedAudio(JNIEnv *jniEnv, jobject instance, jstring inputFile, jstring outputFile, jint cents) {
    const char *inputPath = jniEnv->GetStringUTFChars(inputFile, JNI_FALSE);
    const char *outputPath = jniEnv->GetStringUTFChars(outputFile, JNI_FALSE);

    saveTimeStretchedAudio(inputPath, outputPath, cents);

    jniEnv->ReleaseStringUTFChars(inputFile, inputPath);
    jniEnv->ReleaseStringUTFChars(outputFile, outputPath);
}
