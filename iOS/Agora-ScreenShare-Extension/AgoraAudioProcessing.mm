//
//  AGVideoPreProcessing.m
//  Agora-Screen-Sharing-iOS-Broadcast
//
//  Created by Alex Zheng on 7/28/16.
//  Copyright © 2016 Agora.io All rights reserved.
//

#import <Foundation/Foundation.h>
#import <AgoraRtcKit/IAgoraRtcEngine.h>
#import <AgoraRtcKit/IAgoraMediaEngine.h>
#import <string.h>

#import "AgoraAudioProcessing.h"
#import "AgoraAudioCriticalSection.h"

static const int kAudioBufferPoolSize = 48000 * 8;
static unsigned char mRecordingAudioAppPool[kAudioBufferPoolSize];
static int mRecordingAppBufferBytes = 0;
static CriticalSectionWrapper *CritSect = CriticalSectionWrapper::CreateCriticalSection();

void pushAudioAppFrame(unsigned char *inAudioFrame, int64_t frameSize)
{
    CriticalSectionScoped lock(CritSect);
    
    int remainedSize = kAudioBufferPoolSize - mRecordingAppBufferBytes;
    if (remainedSize >= frameSize) {
        memcpy(mRecordingAudioAppPool+mRecordingAppBufferBytes, inAudioFrame, frameSize);
    } else {
        mRecordingAppBufferBytes = 0;
        memcpy(mRecordingAudioAppPool+mRecordingAppBufferBytes, inAudioFrame, frameSize);
    }
    
    mRecordingAppBufferBytes += frameSize;
}

class AgoraAudioFrameObserver : public agora::media::IAudioFrameObserver
{
public:
    virtual bool onRecordAudioFrame(const char* channelId, AudioFrame& audioFrame) override
    {
        CriticalSectionScoped lock(CritSect);
        
        int bytes = audioFrame.samplesPerChannel * audioFrame.channels * audioFrame.bytesPerSample;
                
        if (mRecordingAppBufferBytes < bytes) {
            return false;
        }
                
        if (mRecordingAppBufferBytes >= bytes) {
            memcpy(audioFrame.buffer, mRecordingAudioAppPool, bytes);
            mRecordingAppBufferBytes -= bytes;
            memmove(mRecordingAudioAppPool, mRecordingAudioAppPool+bytes, mRecordingAppBufferBytes);
        }
        
        return true;
    }
    
    virtual bool onPlaybackAudioFrame(const char* channelId, AudioFrame& audioFrame) override {
        return true;
    }
    
    virtual bool onMixedAudioFrame(const char* channelId, AudioFrame& audioFrame) override {
        return true;
    }
    
    virtual bool onPlaybackAudioFrameBeforeMixing(const char* channelId, agora::rtc::uid_t userId, AudioFrame& audioFrame) override {
        return true;
    }
    virtual AUDIO_FRAME_POSITION getObservedAudioFramePosition() override {
        return AUDIO_FRAME_POSITION_NONE;
    }
    virtual AudioParams getPlaybackAudioParams() override {
        return AudioParams();
    }
    virtual AudioParams getRecordAudioParams() override {
        return AudioParams();
    }
    virtual AudioParams getMixedAudioParams() override {
        return AudioParams();
    }
};

static AgoraAudioFrameObserver s_audioFrameObserver;

@implementation AgoraAudioProcessing
+ (void)registerAudioPreprocessing: (AgoraRtcEngineKit*) kit
{
    if (!kit) {
        return;
    }
    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*)kit.getNativeHandle;
    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
    mediaEngine.queryInterface(rtc_engine, agora::rtc::AGORA_IID_MEDIA_ENGINE);
    if (mediaEngine) {
        mediaEngine->registerAudioFrameObserver(&s_audioFrameObserver);
    }
}

+ (void)deregisterAudioPreprocessing:(AgoraRtcEngineKit*)kit
{
    if (!kit) {
        return;
    }
    
    agora::rtc::IRtcEngine* rtc_engine = (agora::rtc::IRtcEngine*)kit.getNativeHandle;
    agora::util::AutoPtr<agora::media::IMediaEngine> mediaEngine;
    mediaEngine.queryInterface(rtc_engine, agora::rtc::AGORA_IID_MEDIA_ENGINE);
    if (mediaEngine) {
        mediaEngine->registerAudioFrameObserver(NULL);
    }
}

+ (void)pushAudioFrame:(unsigned char *)inAudioFrame withFrameSize:(int64_t)frameSize
{
    pushAudioAppFrame(inAudioFrame, frameSize);
}

@end
