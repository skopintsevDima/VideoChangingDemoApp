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
