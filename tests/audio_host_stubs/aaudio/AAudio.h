#pragma once
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <vector>
#include <utility>
// Hardware-only adapter for tests that execute the real Engine::render and WAV writer.
// Stream opening is opt-in for startup-policy tests; render tests supply samples explicitly.
using aaudio_result_t = int32_t;
using aaudio_data_callback_result_t = int32_t;
struct AAudioStream {
    const float* samples=nullptr;
    int32_t frames=0,channels=1,rate=48000,device=1,preset=2;
    bool owned=false;
    // -1 advertises unavailable position counters, exercising bounded fallback.
    int64_t framesWritten=-1,framesRead=0;
    int32_t readError=0,maxReadFrames=INT32_MAX,readCalls=0,lastRequested=0,xrunCount=0;
    int64_t totalRequested=0;
};
struct AAudioStreamBuilder { int32_t channels=1,rate=48000,device=0,preset=2,performance=1,direction=1; };
enum {
    AAUDIO_OK=0,AAUDIO_CALLBACK_RESULT_CONTINUE=0,
    AAUDIO_ERROR_INTERNAL=-1,AAUDIO_ERROR_INVALID_FORMAT=-2,AAUDIO_ERROR_UNAVAILABLE=-3,
    AAUDIO_FORMAT_PCM_FLOAT=2,AAUDIO_DIRECTION_INPUT=1,AAUDIO_DIRECTION_OUTPUT=0,
    AAUDIO_CONTENT_TYPE_MUSIC=1,AAUDIO_CONTENT_TYPE_SPEECH=2,
    AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION=1,AAUDIO_INPUT_PRESET_VOICE_RECOGNITION=2,
    AAUDIO_PERFORMANCE_MODE_LOW_LATENCY=1,AAUDIO_PERFORMANCE_MODE_NONE=0,
    AAUDIO_SESSION_ID_ALLOCATE=1,AAUDIO_SHARING_MODE_EXCLUSIVE=1,AAUDIO_SHARING_MODE_SHARED=0,
    AAUDIO_USAGE_MEDIA=1,AAUDIO_USAGE_VOICE_COMMUNICATION=2
};
inline int32_t AAudioStream_read(AAudioStream* stream,void* out,int32_t count,int64_t) {
    ++stream->readCalls;stream->lastRequested=count;stream->totalRequested+=count;
    if(stream->readError)return stream->readError;
    int32_t got=std::min({count,stream->frames,stream->maxReadFrames});
    if(got)std::memcpy(out,stream->samples,size_t(got)*stream->channels*sizeof(float));
    stream->frames-=got;stream->framesRead+=got;if(got)stream->samples+=got*stream->channels;return got;
}
namespace host_audio {
inline bool openEnabled=false, failCommunication=false;
inline int actualInput=10, actualOutput=20, actualPreset=0, actualInputRate=0,actualInputChannels=0;
inline std::vector<std::pair<int,int>> inputRequests;
}
inline aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder** out){*out=new AAudioStreamBuilder;return AAUDIO_OK;}
inline aaudio_result_t AAudioStreamBuilder_openStream(AAudioStreamBuilder* b,AAudioStream** out){
    *out=nullptr;
    if(!host_audio::openEnabled)return AAUDIO_ERROR_UNAVAILABLE;
    if(b->direction==AAUDIO_DIRECTION_INPUT){
        host_audio::inputRequests.emplace_back(b->preset,b->performance);
        if(host_audio::failCommunication && b->preset==AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION)return AAUDIO_ERROR_UNAVAILABLE;
    }
    *out=new AAudioStream;
    (*out)->owned=true;(*out)->channels=b->channels>0?b->channels:1;(*out)->rate=b->rate>0?b->rate:48000;
    (*out)->device=b->direction==AAUDIO_DIRECTION_INPUT?host_audio::actualInput:host_audio::actualOutput;
    (*out)->preset=host_audio::actualPreset?host_audio::actualPreset:b->preset;
    if(b->direction==AAUDIO_DIRECTION_INPUT){if(host_audio::actualInputRate)(*out)->rate=host_audio::actualInputRate;if(host_audio::actualInputChannels)(*out)->channels=host_audio::actualInputChannels;}
    return AAUDIO_OK;
}
inline void AAudioStreamBuilder_delete(AAudioStreamBuilder* b){delete b;}
#define HOST_BUILDER_SET(name) template<class...Args> inline void AAudioStreamBuilder_set##name(AAudioStreamBuilder*,Args...){}
HOST_BUILDER_SET(ContentType) HOST_BUILDER_SET(DataCallback) HOST_BUILDER_SET(ErrorCallback)
HOST_BUILDER_SET(Format) HOST_BUILDER_SET(SessionId) HOST_BUILDER_SET(SharingMode) HOST_BUILDER_SET(Usage)
#undef HOST_BUILDER_SET
inline void AAudioStreamBuilder_setChannelCount(AAudioStreamBuilder* b,int value){b->channels=value;}
inline void AAudioStreamBuilder_setDeviceId(AAudioStreamBuilder* b,int value){b->device=value;}
inline void AAudioStreamBuilder_setDirection(AAudioStreamBuilder* b,int value){b->direction=value;}
inline void AAudioStreamBuilder_setInputPreset(AAudioStreamBuilder* b,int value){b->preset=value;}
inline void AAudioStreamBuilder_setPerformanceMode(AAudioStreamBuilder* b,int value){b->performance=value;}
inline void AAudioStreamBuilder_setSampleRate(AAudioStreamBuilder* b,int value){b->rate=value;}
inline aaudio_result_t AAudioStream_close(AAudioStream* stream){if(stream->owned)delete stream;return AAUDIO_OK;}
inline aaudio_result_t AAudioStream_requestStart(AAudioStream*){return AAUDIO_OK;}
inline aaudio_result_t AAudioStream_requestStop(AAudioStream*){return AAUDIO_OK;}
inline int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream*){return 4096;}
inline int32_t AAudioStream_getBufferSizeInFrames(AAudioStream*){return 256;}
inline int32_t AAudioStream_getFramesPerBurst(AAudioStream*){return 256;}
inline int32_t AAudioStream_getXRunCount(AAudioStream* stream){return stream->xrunCount;}
inline int32_t AAudioStream_getDeviceId(AAudioStream* stream){return stream->device;}
inline int32_t AAudioStream_getInputPreset(AAudioStream* stream){return stream->preset;}
inline int32_t AAudioStream_getSessionId(AAudioStream*){return 1;}
inline int32_t AAudioStream_getFormat(AAudioStream*){return AAUDIO_FORMAT_PCM_FLOAT;}
inline int32_t AAudioStream_getChannelCount(AAudioStream* stream){return stream->channels;}
inline int32_t AAudioStream_getSampleRate(AAudioStream* stream){return stream->rate;}
inline int32_t AAudioStream_setBufferSizeInFrames(AAudioStream*,int32_t count){return count;}

inline int64_t AAudioStream_getFramesWritten(AAudioStream* stream){return stream->framesWritten;}
inline int64_t AAudioStream_getFramesRead(AAudioStream* stream){return stream->framesWritten<0?-1:stream->framesRead;}
