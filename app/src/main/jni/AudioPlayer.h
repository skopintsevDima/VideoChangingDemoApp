//
// Created by skopi on 07.11.2017.
//

#ifndef VIDEOCHANGINGDEMOAPP_AUDIOPLAYER_H
#define VIDEOCHANGINGDEMOAPP_AUDIOPLAYER_H

#include <math.h>

#include <SuperpoweredAdvancedAudioPlayer.h>
#include <AndroidIO/SuperpoweredAndroidAudioIO.h>
#include <SuperpoweredTimeStretching.h>

#define HEADROOM_DECIBEL 3.0f
static const float headroom = powf(10.0f, -HEADROOM_DECIBEL * 0.025f);
//TEST CODE

class NDKAudioPlayer {
public:

    NDKAudioPlayer(unsigned int samplerate, unsigned int buffersize, const char *path, int audioFileOffset, int audioFileLength);
    ~NDKAudioPlayer();

    bool process(short *pInt, unsigned int numberOfSamples);
    void onPlayPause(bool play);
    void onCentsChanged(int cents);
    void onTempoChanged(double tempo);

private:

    SuperpoweredAndroidAudioIO *audioSystem;
    SuperpoweredAdvancedAudioPlayer *player;
    float *stereoBuffer;
    float volume;
    int currentSamplerate = 0;
};

#endif

