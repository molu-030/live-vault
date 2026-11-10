#include "audio_core.h"
#include "feedback_guard.h"
#include "record_alignment.h"
#include "local_seek.h"
#include "local_pcm_source.h"
#include "session_transport.h"
#include "recording_journal.h"
#include "input_resampler.h"
#include "echo_input_profile.h"
#include "take_sources.h"
#include "recent_voice.h"
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
#include "audio_backend.h"
#endif
#include <aaudio/AAudio.h>
#include <jni.h>
#include <android/log.h>
#include <chrono>
#include <cstdio>
#include <mutex>
#include <memory>
#include <string>
#include <thread>
#include <unistd.h>
#include "composer_engine.h"
#include "vocal_harmony.h"
#include "vocal_looper.h"

using namespace lm;

static double monoSeconds() {
    timespec t{};
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec + t.tv_nsec / 1e9;
}

class Engine {
public:
    std::atomic<float> params[20];
    AAudioStream *input = nullptr, *output = nullptr;
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
    std::unique_ptr<lv::OboeBackend> oboe;
    std::atomic<int> requestedBackend{0},appliedBackend{-1},platformApi{-1},backendFallback{0};
    bool suppressOboeAttempt=false;
    std::atomic<bool> oboeInputActive{false};
#endif
    std::recursive_mutex streamControl;
    // Control-only lifecycle lock. Ordering is streamControl -> recordControl.
    std::mutex recordControl;
    std::atomic<int> inputDeviceSnapshot{0},inputSessionSnapshot{0},outputDeviceSnapshot{0};
    std::atomic<AAudioStream*> publishedInput{nullptr};
    lv::ComposerEngine composer;
    SessionTransport session;
    std::atomic<bool> sessionRecordBus{false};
    vh::Harmony harmony;
    vh::Looper vocalLooper;
    std::mutex vocalControl;
    std::atomic<bool> vocalMonitor{true};
    std::thread voiceAnalyzer;
    std::array<float, 8192> composerMusic{}, composerVoice{};
    std::array<float, 4096> composerCount{};
    std::atomic<bool> running{false}, recording{false}, writerRunning{false}, recordPaused{false}, segmentPending{false};
    std::atomic<int> recordWriters{0}, fault{0};
    std::atomic<uint64_t> frames{0}, recordFrames{0};
    std::atomic<float> frequency{0}, inRms{0}, outRms{0}, beat{0};
    std::atomic<bool> overflow{false}, diskError{false};
    std::atomic<double> recordStartClock{0};
    std::atomic<uint64_t> segmentSourceStart{0};
    uint64_t segmentOutputStart = 0;
    std::vector<RecordedSpan> recordedSpans;
    RecordAlignment alignment;
    int recordSyncMs = 0;
    std::atomic<bool> recordMicOnly{false};
    std::atomic<int> requestedBursts{2};
    std::atomic<float> guideFrequency{0}, pitchConfidence{0}, pitchLevel{0};
    float guideEnvelope = 0, lastGuideFrequency = 440;
    int sr = 48000;
    int inputRate = 48000;
    int inputChannels = 1;
    DSP dsp;
    PlaybackMixer playback;
    FeedbackGuard feedback;
    int monitorMode = 0;
    std::atomic<float> monitorLevel{.18f};
    std::atomic<bool> monitorLevelChosen{false};
    Ring<32768> pitchRing;
    Ring<1048576> recordRing;
    Ring<1048576> sourceRing;
    std::array<float,20480> sourceBuffer{};
    std::atomic<bool> sourceRecording{false},sourceError{false},sourceEditable{true},vocalStemError{true};
    RecentVoice recentVoice;
    std::thread analyzer, writer;
    std::mutex notesMutex;
    std::vector<Note> notes;
    Note active;
    bool hasActive = false;
    double candidateTime = 0, lastSeen = 0, recordStartTime = 0;
    int candidate = -1, candidateCount = 0;
    std::array<float, 4096> inputBuffer{};
    std::array<float, 8192> rawInputBuffer{};
    std::array<float, 8192> recordBuffer{}, capturedBuffer{};
    uint32_t noiseState = 12345678;
    double transport = 0;
    bool bandWasOn = false;
    std::unique_ptr<LocalPcmSource> localSource{new LocalPcmSource},retiredLocalSource;
    std::mutex localControl;
    bool localLoadInProgress=false;
    std::atomic<uint64_t> localLoadEpoch{0};
    std::atomic<bool> localDesiredPlay{false};
    std::atomic<uint64_t> localMissingFrames{0},localRecordingMissingFrames{0};
    std::atomic<uint64_t> writerDroppedFrames{0},writerIoErrors{0};
    std::array<std::atomic<double>,21> diagnostic{};
    std::atomic<int> outputBurstSnapshot{0},outputCapacitySnapshot{0},outputBufferSnapshot{0},outputRateSnapshot{48000},outputXrunsSnapshot{0};
    std::atomic<uint64_t> inputShortReads{0},inputMissingFrames{0};
    std::atomic<double> localDurationSeconds{0};
    int localRate = 48000;
    std::atomic<bool> localPlaying{false};
    std::atomic<uint64_t> localCursor{0};
    std::atomic<bool> localBusy{false};
    LocalSeek localSeek;
    InputResampler inputResampler;
    std::mutex echoInputControl;
    EchoInputProfile echoInputConfig;
    std::atomic<int> inputProfile{0};

    Engine() {
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        oboe=std::make_unique<lv::OboeBackend>(
                [](void* user,float* stereo,int32_t frames){static_cast<Engine*>(user)->render(stereo,frames);},
                [](void* user,int32_t code){auto* self=static_cast<Engine*>(user);self->params[11]=0;self->fault=code;self->running=false;self->diagnostic[7]=-1;self->diagnostic[8]=0;},this);
#endif
        for(auto& value:diagnostic)value=-1;
        diagnostic[0]=1;diagnostic[3]=diagnostic[4]=diagnostic[5]=diagnostic[6]=diagnostic[8]=0;
        diagnostic[9]=diagnostic[10]=diagnostic[12]=diagnostic[13]=0;diagnostic[11]=LocalPcmSource::pageFrames*LocalPcmSource::pageCount;
        float defaults[] = {1, .55f, .27f, 2.2f, .14f, 280, .25f, 0, 0, 1, 1, 0, 0, 0, 0, 78, 0, 0, 0, 1};
        for (int i = 0; i < 20; i++) params[i] = defaults[i];
    }

    ~Engine() {
        stop();
    }

    static aaudio_data_callback_result_t data(AAudioStream* stream, void* user, void* buffer, int32_t n) {
        auto* self=static_cast<Engine*>(user);
        self->outputXrunsSnapshot=AAudioStream_getXRunCount(stream);
        self->outputBufferSnapshot=AAudioStream_getBufferSizeInFrames(stream);
        self->outputRateSnapshot=AAudioStream_getSampleRate(stream);
        self->diagnostic[1]=self->outputXrunsSnapshot.load();
        self->diagnostic[19]=self->outputBufferSnapshot.load();self->diagnostic[20]=self->outputRateSnapshot.load();
        return self->render(static_cast<float*>(buffer),n);
    }

    static void error(AAudioStream*, void* user, aaudio_result_t e) {
        auto* self = static_cast<Engine*>(user);
        self->params[11] = 0;
        self->fault = e;
        self->running = false;self->diagnostic[7]=-1;self->diagnostic[8]=0;
    }

#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
    bool usingOboe() const {return appliedBackend.load()==1 && oboe->hasOutput();}
    void refreshOboeSnapshots() {
        if(!usingOboe())return;
        auto info=oboe->outputInfo();
        outputBurstSnapshot=info.framesPerBurst;outputCapacitySnapshot=info.bufferCapacity;
        outputBufferSnapshot=info.bufferSize;outputRateSnapshot=info.sampleRate;
        outputXrunsSnapshot=info.xruns;
        diagnostic[1]=info.xruns;diagnostic[19]=info.bufferSize;diagnostic[20]=info.sampleRate;
    }
    bool fallbackStart(int device,int outputDevice,int mode,bool withMic,bool preserve,int reason){
        stop(preserve);
        backendFallback=reason;
        suppressOboeAttempt=true;
        bool started=start(device,outputDevice,mode,withMic,preserve);
        suppressOboeAttempt=false;
        return started;
    }
#endif

    bool start(int device, int outputDevice, int mode, bool withMic = true, bool preserve = false) {
        std::lock_guard<std::recursive_mutex> control(streamControl);
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if ((output || usingOboe()) && running) {
            if (withMic && !input && !oboe->hasInput()) return openMicrophone(device, mode);
            return true;
        }
        if (output || usingOboe()) stop(preserve);
#else
        if (output && running) {
            if (withMic && !input) return openMicrophone(device, mode);
            return true;
        }
        if (output) stop(preserve);
#endif
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(!suppressOboeAttempt)backendFallback=0;
#endif
        guideFrequency = 0;
        guideEnvelope = 0;
        pitchConfidence = 0;
        pitchLevel = 0;
        if (!preserve) playback.reset();
        params[11] = 0;
        monitorMode = mode;
        if (preserve) {
            monitorLevel = std::min(monitorLevel.load(), .18f);
        } else {
            monitorLevel = .18f;
            monitorLevelChosen = false;
        }
        fault = 0;
        frequency = 0;
        if (!preserve) {
            frames = 0;
            transport = 0;
        }
        pitchRing.reset();
        {
            std::lock_guard<std::mutex> lock(notesMutex);
            notes.clear();
            hasActive = false;
            candidate = -1;
            candidateCount = 0;
        }

#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        bool oboeOpened=false;
        if(requestedBackend==1 && !suppressOboeAttempt){
            int result=oboe->openOutput(outputDevice,mode);
            if(result==0){
                oboeOpened=true;appliedBackend=1;platformApi=0;
            }else{
                backendFallback=1;fault=result;
            }
        }
        if(!oboeOpened){appliedBackend=-1;platformApi=-1;}
        if(!oboeOpened){
#endif
        AAudioStreamBuilder* b = nullptr;
        AAudio_createStreamBuilder(&b);
        AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(b, 2);
        if (outputDevice > 0) AAudioStreamBuilder_setDeviceId(b, outputDevice);
        AAudioStreamBuilder_setUsage(b, mode == 1 ? AAUDIO_USAGE_VOICE_COMMUNICATION : AAUDIO_USAGE_MEDIA);
        AAudioStreamBuilder_setContentType(b, mode == 1 ? AAUDIO_CONTENT_TYPE_SPEECH : AAUDIO_CONTENT_TYPE_MUSIC);
        AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSharingMode(b, mode == 2 ? AAUDIO_SHARING_MODE_SHARED : AAUDIO_SHARING_MODE_EXCLUSIVE);
        AAudioStreamBuilder_setDataCallback(b, data, this);
        AAudioStreamBuilder_setErrorCallback(b, error, this);

        auto result = AAudioStreamBuilder_openStream(b, &output);
        if (result != AAUDIO_OK) {
            AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
            result = AAudioStreamBuilder_openStream(b, &output);
        }
        if (result != AAUDIO_OK) {
            AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_NONE);
            result = AAudioStreamBuilder_openStream(b, &output);
        }
        AAudioStreamBuilder_delete(b);

        if (result != AAUDIO_OK) {
            fault = result;
            output = nullptr;
            return false;
        }
        int actualOut = AAudioStream_getDeviceId(output);
        if (outputDevice > 0 && actualOut > 0 && actualOut != outputDevice) {
            AAudioStream_close(output);
            output = nullptr;
            fault = AAUDIO_ERROR_UNAVAILABLE;
            return false;
        }
        if (AAudioStream_getFormat(output) != AAUDIO_FORMAT_PCM_FLOAT || AAudioStream_getChannelCount(output) != 2) {
            AAudioStream_close(output);
            output = nullptr;
            fault = AAUDIO_ERROR_INVALID_FORMAT;
            return false;
        }
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        appliedBackend=0;platformApi=0;
        }
#endif

        int oldRate = sr;
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        sr = oboeOpened ? oboe->outputInfo().sampleRate : AAudioStream_getSampleRate(output);
#else
        sr = AAudioStream_getSampleRate(output);
#endif
        if (oldRate > 0 && oldRate != sr && localCursor.load() > 0) {
            localCursor = uint64_t(std::llround(double(localCursor.load()) * sr / oldRate));
        }
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(oboeOpened)oboe->setOutputBufferBursts(requestedBursts.load());
        else
#endif
        AAudioStream_setBufferSizeInFrames(
                output,
                std::min(AAudioStream_getBufferCapacityInFrames(output), AAudioStream_getFramesPerBurst(output) * requestedBursts.load())
        );
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(oboeOpened){outputDeviceSnapshot=oboe->outputInfo().deviceId;refreshOboeSnapshots();}
        else{
#endif
        outputDeviceSnapshot=AAudioStream_getDeviceId(output);
        outputBurstSnapshot=AAudioStream_getFramesPerBurst(output);outputCapacitySnapshot=AAudioStream_getBufferCapacityInFrames(output);
        outputBufferSnapshot=AAudioStream_getBufferSizeInFrames(output);outputRateSnapshot=sr;
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        }
#endif
        diagnostic[19]=outputBufferSnapshot.load();diagnostic[20]=sr;
        dsp.init(sr);
        feedback.init(sr, true, preserve);
        composer.sampleRate(sr);
        {
            std::lock_guard<std::mutex> lock(vocalControl);
            if (!vocalLooper.sampleRate(sr)) {
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
                if(oboeOpened)oboe->closeOutput();else
#endif
                AAudioStream_close(output);
                output = nullptr;
                fault = AAUDIO_ERROR_INTERNAL;
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
                appliedBackend=-1;platformApi=-1;
#endif
                return false;
            }
        }
        running = true;
        if (withMic && !openMicrophone(device, mode)) {
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
            if(oboeOpened)return fallbackStart(device,outputDevice,mode,withMic,preserve,backendFallback.load());
#endif
            stop(preserve);
            return false;
        }
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(oboeOpened){
            int started=oboe->startOutput();
            if(started!=0){fault=started;return fallbackStart(device,outputDevice,mode,withMic,preserve,3);}
        }else{
#endif
        auto startResult = AAudioStream_requestStart(output);
        if (startResult != AAUDIO_OK) {
            fault = startResult;
            stop(preserve);
            return false;
        }
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        }
#endif
        fault=0; // A successful fallback has no active fault; retain backendFallback separately.
        analyzer = std::thread([this] { analyze(); });
        voiceAnalyzer = std::thread([this] { composer.analyze(running); });
        return true;
    }

    bool openMicrophone(int device, int mode) {
        std::lock_guard<std::recursive_mutex> control(streamControl);
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(oboe->hasInput())return true;
#endif
        if (input) return true;
        EchoInputProfile profile;
        {
            std::lock_guard<std::mutex> lock(echoInputControl);
            profile = echoInputConfig;
        }
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(usingOboe()){
            bool speakerEcho=mode==0 && profile.useFor(device,outputDeviceSnapshot.load());
            bool communication=mode>0 || speakerEcho;
            int result=oboe->openInput(device,sr,communication,speakerEcho,
                    [](void* user,int32_t id){
                        auto* self=static_cast<Engine*>(user);
                        std::lock_guard<std::mutex> lock(self->echoInputControl);
                        return self->echoInputConfig.builtinMicrophone(id);
                    });
            if(result!=0){fault=result;backendFallback=2;return false;}
            result=oboe->startInput();
            if(result!=0){oboe->closeInput();fault=result;backendFallback=3;return false;}
            // Oboe exposes input metadata only after startInput publishes its stream.
            // Keep the render gate closed until the negotiated format and SRC are ready.
            auto info=oboe->inputInfo();
            if(info.format!=AAUDIO_FORMAT_PCM_FLOAT || info.channels<1 || info.channels>2 || info.sampleRate<=0){
                oboe->closeInput();fault=AAUDIO_ERROR_INVALID_FORMAT;backendFallback=3;return false;
            }
            inputShortReads=0;inputMissingFrames=0;
            diagnostic[3]=diagnostic[4]=diagnostic[5]=diagnostic[6]=0;
            inputResampler.reset(info.sampleRate,sr,true);
            inputChannels=info.channels;inputRate=info.sampleRate;monitorMode=mode;
            if(!monitorLevelChosen)monitorLevel=feedback.isEnabled()?.18f:1.f;
            inputProfile=info.inputPreset==AAUDIO_INPUT_PRESET_VOICE_RECOGNITION?1
                    :info.inputPreset==AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION?(speakerEcho?3:2):0;
            inputDeviceSnapshot=info.deviceId;inputSessionSnapshot=info.sessionId;
            oboeInputActive.store(true,std::memory_order_release);
            return true;
        }
#endif
        bool speakerEcho = mode == 0 && output && profile.useFor(device, AAudioStream_getDeviceId(output));
        bool communication = mode > 0 || speakerEcho;
        AAudioStreamBuilder* b = nullptr;
        AAudio_createStreamBuilder(&b);
        AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_INPUT);
        AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(b, 1);
        AAudioStreamBuilder_setSampleRate(b, sr);
        AAudioStreamBuilder_setPerformanceMode(b, communication ? AAUDIO_PERFORMANCE_MODE_NONE : AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setInputPreset(b, communication ? AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION : AAUDIO_INPUT_PRESET_VOICE_RECOGNITION);
        AAudioStreamBuilder_setSessionId(b, AAUDIO_SESSION_ID_ALLOCATE);
        if (device > 0) AAudioStreamBuilder_setDeviceId(b, device);
        AAudioStreamBuilder_setErrorCallback(b, error, this);

        auto openInput = [&]() {
            auto result = AAudioStreamBuilder_openStream(b, &input);
            if (result != AAUDIO_OK) {
                AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_NONE);
                AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
                result = AAudioStreamBuilder_openStream(b, &input);
            }
            if (result != AAUDIO_OK) {
                AAudioStreamBuilder_setSampleRate(b, 0);
                AAudioStreamBuilder_setChannelCount(b, 0);
                result = AAudioStreamBuilder_openStream(b, &input);
            }
            return result;
        };
        auto result = openInput();
        // A default input may change after device enumeration. Do not keep the
        // special preset if the opened microphone cannot be verified as built-in.
        if (speakerEcho && (result != AAUDIO_OK || !profile.builtinMicrophone(AAudioStream_getDeviceId(input)))) {
            if (input) AAudioStream_close(input);
            input = nullptr;
            speakerEcho = false;
            AAudioStreamBuilder_setInputPreset(b, AAUDIO_INPUT_PRESET_VOICE_RECOGNITION);
            AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
            result = openInput();
        }
        AAudioStreamBuilder_delete(b);

        if (result != AAUDIO_OK) {
            fault = result;
            input = nullptr;
            return false;
        }
        int actualIn = AAudioStream_getDeviceId(input);
        if (device > 0 && actualIn > 0 && actualIn != device) {
            AAudioStream_close(input);
            input = nullptr;
            fault = AAUDIO_ERROR_UNAVAILABLE;
            return false;
        }
        int ch = AAudioStream_getChannelCount(input);
        int rate = AAudioStream_getSampleRate(input);
        if (AAudioStream_getFormat(input) != AAUDIO_FORMAT_PCM_FLOAT || ch < 1 || ch > 2 || rate <= 0) {
            AAudioStream_close(input);
            input = nullptr;
            fault = AAUDIO_ERROR_INVALID_FORMAT;
            return false;
        }
        inputShortReads=0;inputMissingFrames=0;
        diagnostic[3]=diagnostic[4]=diagnostic[5]=diagnostic[6]=0;
        inputResampler.reset(rate, sr, true);
        inputChannels = ch;
        inputRate = rate;
        monitorMode = mode;
        if (!monitorLevelChosen) monitorLevel = feedback.isEnabled() ? .18f : 1.f;
        result = AAudioStream_requestStart(input);
        if (result != AAUDIO_OK) {
            AAudioStream_close(input);
            input = nullptr;
            fault = result;
            return false;
        }
        int actualPreset = AAudioStream_getInputPreset(input);
        inputProfile = actualPreset == AAUDIO_INPUT_PRESET_VOICE_RECOGNITION ? 1
                : actualPreset == AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION ? (speakerEcho ? 3 : 2) : 0;
        inputDeviceSnapshot=AAudioStream_getDeviceId(input);inputSessionSnapshot=AAudioStream_getSessionId(input);
        publishedInput.store(input, std::memory_order_release);
        return true;
    }

    void microphoneOff(bool preserve = false) {
        std::lock_guard<std::recursive_mutex> control(streamControl);
        inputDeviceSnapshot=inputSessionSnapshot=0;
        inputProfile = 0;
        diagnostic[2]=-1;diagnostic[7]=-1;diagnostic[8]=0;diagnostic[17]=-1;diagnostic[18]=-1;
        publishedInput.store(nullptr, std::memory_order_release);
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        oboeInputActive.store(false,std::memory_order_release);
#endif
        params[11] = 0;
        if (!preserve) harmony.disable();
        harmony.clearPitch();
        frequency = 0;
        pitchConfidence = 0;
        pitchLevel = 0;
        if (!preserve) vocalLooper.cancel();
        while (recordWriters.load() > 0) std::this_thread::yield();
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(oboe->hasInput())oboe->closeInput();
#endif
        if (input) {
            AAudioStream_requestStop(input);
            AAudioStream_close(input);
            input = nullptr;
        }
        recentVoice.forget();
        harmony.clearPitch();
    }

    void stop(bool preserve = false) {
        std::lock_guard<std::recursive_mutex> control(streamControl);
        outputDeviceSnapshot=0;
        guideFrequency = 0;
        running = false;
        if (!preserve) {
            harmony.disable();
            vocalLooper.transport(0);
        }
        stopRecord();
        if (output) {
            AAudioStream_requestStop(output);
            AAudioStream_close(output);
            output = nullptr;
        }
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if(oboe->hasOutput())oboe->closeOutput();
        appliedBackend=-1;platformApi=-1;
#endif
        outputBurstSnapshot=outputCapacitySnapshot=outputBufferSnapshot=outputXrunsSnapshot=0;
        diagnostic[1]=diagnostic[19]=diagnostic[20]=-1;
        microphoneOff(preserve);
        if (voiceAnalyzer.joinable()) voiceAnalyzer.join();
        if (analyzer.joinable()) analyzer.join();
        frequency = 0;
        inRms = 0;
        outRms = 0;
        params[11] = 0;
        if (!preserve) {
            localDesiredPlay=false;localPlaying=false;localLoadEpoch.fetch_add(1);
            std::unique_lock<std::mutex> lock(localControl,std::try_to_lock);if(lock.owns_lock())localSource->idle();
        }
    }

    float noise() {
        noiseState ^= noiseState << 13;
        noiseState ^= noiseState >> 17;
        noiseState ^= noiseState << 5;
        return int32_t(noiseState) / 2147483648.f;
    }

    float band(const float* p, uint64_t sampleFrame) {
        bool on = p[13] > .5f;
        if (on && !bandWasOn) transport = 0;
        bandWasOn = on;
        if (!on && p[17] < .5f) return 0;
        double b = transport, phase = b - std::floor(b);
        transport += p[15] / (60. * sr);
        beat = b;
        float sample = 0;
        if (on) {
            int style = int(p[14]);
            int chords[4][4] = {{0, 9, 5, 7}, {0, 9, 2, 7}, {0, 7, 9, 5}, {9, 5, 0, 7}};
            int bar = int(b / 4) % 4, root = 48 + chords[style % 4][bar] + int(p[16]);
            bool minor = (root - int(p[16])) % 12 == 9 || (root - int(p[16])) % 12 == 2;
            double sec = double(sampleFrame) / sr;
            float within = std::fmod(b, 4.);
            float env = std::exp(-within * (style == 2 ? 1.7f : .35f));
            int intervals[] = {0, minor ? 3 : 4, 7, 11};
            for (int v = 0; v < (style == 1 ? 4 : 3); v++) {
                float f = hz(root + 12 + intervals[v]);
                float arpeggio = style == 2 ? std::exp(-std::fmod(b + v * .5, 2.) * 4.) : env;
                sample += (std::sin(2 * PI * f * sec) + .16 * std::sin(4 * PI * f * sec)) * .055f * arpeggio;
            }
            sample += std::sin(2 * PI * hz(root - 12) * sec) * .10f * std::exp(-phase * 3.f);
            if (style != 2 || int(b) % 4 == 0) {
                float t = phase * 60.f / p[15];
                sample += std::sin(2 * PI * (48 * t + 5 * (1 - std::exp(-t * 25)))) * .27 * std::exp(-t * 20);
            }
            if (int(b) % 2 == 1) sample += noise() * .11f * std::exp(-phase * 18);
            double hat = std::fmod(b * (style == 3 ? 4 : 2), 1.);
            sample += noise() * .035f * std::exp(-hat * 25);
        }
        if (p[17] > .5f) {
            float t = phase * 60.f / p[15];
            sample += std::sin(2 * PI * (int(b) % 4 == 0 ? 1500 : 1000) * t) * .12f * std::exp(-t * 90);
        }
        return sample * p[1];
    }

    aaudio_data_callback_result_t render(float* out, int n) {
        if (!running) {
            std::fill(out, out + n * 2, 0);
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        }
        if (n > 4096) {
            for (int offset = 0; offset < n; offset += 4096) {
                render(out + offset * 2, std::min(4096, n - offset));
            }
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        }
        recordWriters.fetch_add(1);
        AAudioStream* mic = publishedInput.load(std::memory_order_acquire);
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        bool oboeMic=oboeInputActive.load(std::memory_order_acquire);
        bool haveMic=mic || oboeMic;
#else
        bool haveMic=mic;
#endif
        if (!haveMic) {
            std::fill(inputBuffer.begin(), inputBuffer.begin() + n, 0);
        } else {
            int nominal=std::max(1,int(std::ceil(double(n)*inputRate/sr)));
            int target=std::max(1,int(std::lround(inputRate*.008)));
            int64_t written=-1,read=-1;
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
            int32_t oboeAvailable=oboeMic?oboe->availableInputFrames():-1;
            if(oboeMic && oboeAvailable>=0){written=oboeAvailable;read=0;}
            else if(!oboeMic){written=AAudioStream_getFramesWritten(mic);read=AAudioStream_getFramesRead(mic);}
#else
            written=AAudioStream_getFramesWritten(mic);read=AAudioStream_getFramesRead(mic);
#endif
            size_t budget=size_t(nominal+target*2);
            if(written>=0&&read>=0&&written>=read)budget=std::min<uint64_t>(budget,uint64_t(written-read));
            budget=std::min<size_t>(budget,std::min<size_t>(8192,inputResampler.freeFrames()));
            while(budget){
                int requested=int(std::min<size_t>(budget,4096));
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
                int got=oboeMic?oboe->readInput(rawInputBuffer.data(),requested):AAudioStream_read(mic,rawInputBuffer.data(),requested,0);
#else
                int got=AAudioStream_read(mic,rawInputBuffer.data(),requested,0);
#endif
                if(got<0){fault=got;params[11]=0;running=false;inputResampler.reset(inputRate,sr,true);break;}
                if(got<requested){inputShortReads.fetch_add(1,std::memory_order_relaxed);inputMissingFrames.fetch_add(requested-got,std::memory_order_relaxed);}
                inputResampler.push(rawInputBuffer.data(),size_t(got),inputChannels);
                budget-=size_t(got);if(got<requested)break;
            }
            inputResampler.render(inputBuffer.data(),n);
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
            diagnostic[2]=oboeMic?-1:AAudioStream_getXRunCount(mic);
#else
            diagnostic[2]=AAudioStream_getXRunCount(mic);
#endif
            diagnostic[3]=inputShortReads.load();diagnostic[4]=inputMissingFrames.load();
            diagnostic[5]=inputResampler.underflowFrames();diagnostic[6]=inputResampler.overflowFrames();
            diagnostic[7]=running?inputResampler.correctionPpm():-1;diagnostic[8]=running?inputResampler.driftState():0;
            diagnostic[17]=running?inputResampler.targetDelayMs()*sr/1000.:-1;diagnostic[18]=running?sr:-1;
        }

#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
        if (mic || oboeMic) recentVoice.push(inputBuffer.data(), n, sr);
#else
        if (mic) recentVoice.push(inputBuffer.data(), n, sr);
#endif
        pitchRing.push(inputBuffer.data(), n);
        composer.microphone.push(inputBuffer.data(), n);
        bool sessionActive=session.begin(sr);
        bool sessionSuppressed=session.isEnabled();
        bool callbackRecording=recording.load()&&!recordPaused.load()&&(!sessionRecordBus.load()||(sessionActive&&session.playing()&&session.captureArmed()));
        if(sessionSuppressed&&!sessionActive){std::fill(composerMusic.begin(),composerMusic.begin()+n*2,0);std::fill(composerVoice.begin(),composerVoice.begin()+n*2,0);std::fill(composerCount.begin(),composerCount.begin()+n,0);}
        if(!sessionSuppressed)composer.render(composerMusic.data(), composerVoice.data(), composerCount.data(), n);
        float p[20];
        for (int i = 0; i < 20; i++) p[i] = params[i].load(std::memory_order_relaxed);
        dsp.configure(p);
        feedback.prepare(sr);
        float inE = 0, outE = 0;
        uint64_t base = frames.load(std::memory_order_relaxed);
        bool local = localPlaying.load(std::memory_order_acquire);
        localBusy = true;
        local = local && localPlaying.load(std::memory_order_acquire);
        bool localSourceUsed=local;
        uint64_t cursor=local?localCursor.load():0;
        double target=local?localSeek.consume():-1;
        if(target>=0){cursor=uint64_t(target*sr);if(local)localSource->seek(uint64_t(target*localRate));}
        if(local)localSource->prefetch(uint64_t(double(cursor)*localRate/sr));
        bool cacheMiss=false;
        playback.read(capturedBuffer.data(), n * 2);

        for (int i = 0; i < n; i++) {
            float l, r;
            dsp.process(inputBuffer[i], p, l, r);
            if(sessionActive)composer.render(composerMusic.data()+i*2,composerVoice.data()+i*2,composerCount.data()+i,1,true,session.beat(),session.playing(),i==0);
            float sessionL=0,sessionR=0;
            if(sessionActive)session.sample(sessionL,sessionR,callbackRecording);
            float music = sessionSuppressed?0:band(p, base + i);
            float ml = music + composerMusic[i * 2] * p[1];
            float mr = music + composerMusic[i * 2 + 1] * p[1];
            ml+=sessionL;mr+=sessionR;
            if(local&&!sessionSuppressed){
                double pos=double(cursor)*localRate/sr;
                if(pos+1<localSource->frames()){
                    float sampleL=0,sampleR=0;
                    if(localSource->sample(pos,sampleL,sampleR)){ml+=sampleL*p[1];mr+=sampleR*p[1];}
                    else {cacheMiss=true;localMissingFrames.fetch_add(1,std::memory_order_relaxed);if(recording&&!recordPaused)localRecordingMissingFrames.fetch_add(1,std::memory_order_relaxed);}
                    ++cursor; // Output clock remains master, including missing data.
                    if(localSource->ioErrors()){local=false;localPlaying=false;localSource->idle();}
                }else{local=false;localPlaying=false;localSource->idle();}
            }
            float synth = 0, f = frequency.load(std::memory_order_relaxed);
            if (p[12] > .5 && f > 0) {
                synth = std::sin(2 * PI * hz(int(std::round(69 + 12 * std::log2(f / 440)))) * double(base + i) / sr) * .07f;
            }
            float vocalL, vocalR, loopL, loopR, loopClick;
            harmony.tick(l, r, haveMic ? f : 0, haveMic ? pitchConfidence.load(std::memory_order_relaxed) : 0, haveMic ? pitchLevel.load(std::memory_order_relaxed) : 0, sr, vocalL, vocalR);
            vocalLooper.tick(vocalL, vocalR, loopL, loopR, &loopClick);
            if(!sessionSuppressed){ml += loopL;mr += loopR;}
            bool mixVocal = !composer.enabled() || composer.mixVoice.load();
            float vl = (mixVocal ? vocalL + synth : 0) + composerVoice[i * 2];
            float vr = (mixVocal ? vocalR + synth : 0) + composerVoice[i * 2 + 1];
            float bl = ml + capturedBuffer[i * 2];
            float br = mr + capturedBuffer[i * 2 + 1];
            bool micOnly = recordMicOnly.load(std::memory_order_relaxed);
            if (micOnly) { vl = l; vr = r; bl = br = 0; }
            if(sessionRecordBus.load(std::memory_order_relaxed))bl=br=0;
            // Preserve the original voice bus and its recording sync. This stem
            // includes routed voice instruments; loops remain on the fixed bed.
            sourceBuffer[i*5] = inputBuffer[i];
            sourceBuffer[i*5+1] = bl;
            sourceBuffer[i*5+2] = br;
            sourceBuffer[i*5+3] = vl;
            sourceBuffer[i*5+4] = vr;
            if (recording && !micOnly && (harmony.enabled() || !mixVocal || p[12] > .5f || composer.enabled())) sourceEditable = false;
            if (callbackRecording) alignment.tick(vl, vr, bl, br);
            float recL = limit(vl + bl), recR = limit(vr + br);
            float liveGate = p[11] * float(vocalMonitor.load(std::memory_order_relaxed));
            float liveL = (mixVocal ? (vocalL + synth) * liveGate : 0) + composerVoice[i * 2] * composer.monitoring();
            float liveR = (mixVocal ? (vocalR + synth) * liveGate : 0) + composerVoice[i * 2 + 1] * composer.monitoring();
            liveL *= monitorLevel.load(std::memory_order_relaxed);
            liveR *= monitorLevel.load(std::memory_order_relaxed);
            feedback.process(liveL, liveR);
            float gf = guideFrequency.load();
            if (gf > 0) lastGuideFrequency = gf;
            guideEnvelope += ((gf > 0 ? 1.f : 0.f) - guideEnvelope) * .002f;
            float guide = .07f * guideEnvelope * (std::sin(2 * PI * lastGuideFrequency * (base + i) / sr) + .22f * std::sin(4 * PI * lastGuideFrequency * (base + i) / sr));
            out[i * 2] = limit(ml + liveL + guide + composerCount[i] + loopClick);
            out[i * 2 + 1] = limit(mr + liveR + guide + composerCount[i] + loopClick);
            recordBuffer[i * 2] = recL;
            recordBuffer[i * 2 + 1] = recR;
            inE += inputBuffer[i] * inputBuffer[i];
            outE += out[i * 2] * out[i * 2];
        }
        session.end();
        if(cacheMiss)localSource->underrun();
        if(localSourceUsed)localCursor=cursor;
        if(localSourceUsed)cacheLocalDiagnostics();
        localBusy = false;
        frames.fetch_add(n, std::memory_order_relaxed);
        inRms = std::sqrt(inE / n);
        outRms = std::sqrt(outE / n);
        if (callbackRecording) {
            if (segmentPending.exchange(false)) segmentSourceStart = base;
            if (recordFrames.load() == 0) recordStartClock = monoSeconds();
            if (sourceRecording && sourceRing.available() + size_t(n*5) <= 1048576) {
                if (sourceRing.push(sourceBuffer.data(), n*5) != size_t(n*5)) sourceError = true;
            } else if (sourceRecording) sourceError = true;
            size_t written = recordRing.push(recordBuffer.data(), n * 2);
            if(written!=size_t(n*2)){overflow=true;writerDroppedFrames.fetch_add((size_t(n*2)-written)/2,std::memory_order_relaxed);}
            recordFrames.fetch_add(n);
        }
        recordWriters.fetch_sub(1);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    void finalize(double end) {
        if (hasActive) {
            active.end = std::max(active.start, end);
            if (active.end - active.start >= .06) notes.push_back(active);
            hasActive = false;
            if (notes.size() > 20000) notes.erase(notes.begin(), notes.begin() + 1000);
        }
    }

    void analyze() {
        std::array<float, 2048> window{};
        std::array<float, 4096> fresh{};
        size_t filled = 0;
        uint64_t lastAnalyzed = 0;
        int feedbackTick = 0;
        while (running) {
            size_t n = pitchRing.pop(fresh.data(), fresh.size());
            if (n) {
                if (n >= window.size()) {
                    std::copy(fresh.begin() + n - window.size(), fresh.begin() + n, window.begin());
                    filled = window.size();
                } else {
                    std::move(window.begin() + n, window.end(), window.begin());
                    std::copy(fresh.begin(), fresh.begin() + n, window.end() - n);
                    filled = std::min(window.size(), filled + n);
                }
            }
            uint64_t nowFrame = frames.load();
            if (filled == window.size() && nowFrame - lastAnalyzed >= uint64_t(sr / 50)) {
                lastAnalyzed = nowFrame;
                if (++feedbackTick % 5 == 0 && ((params[11] > .5f && vocalMonitor.load()) || (composer.enabled() && composer.monitoring())) && monitorLevel > 0) {
                    feedback.analyze(window.data(), window.size(), sr, double(nowFrame) / sr);
                }
                Pitch pitch = detectPitch(window.data(), window.size(), sr);
                frequency = pitch.frequency;
                pitchConfidence = pitch.confidence;
                pitchLevel = pitch.rms;
                double t = std::max(0., double(nowFrame) / sr - .022);
                std::lock_guard<std::mutex> lock(notesMutex);
                if (pitch.frequency > 0) {
                    int midi = std::lround(69 + 12 * std::log2(pitch.frequency / 440));
                    if (candidate != midi) {
                        candidate = midi;
                        candidateCount = 1;
                        candidateTime = t;
                    } else {
                        candidateCount++;
                    }
                    if (hasActive && active.midi == midi) {
                        lastSeen = t;
                        active.end = t;
                    } else if (candidateCount >= 3) {
                        finalize(candidateTime);
                        active = {midi, std::clamp(int(pitch.rms * 350) + 45, 30, 127), candidateTime, t};
                        hasActive = true;
                        lastSeen = t;
                    }
                } else {
                    candidate = -1;
                    candidateCount = 0;
                    if (hasActive && t - lastSeen > .09) finalize(lastSeen + .025);
                }
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(8));
        }
        std::lock_guard<std::mutex> lock(notesMutex);
        finalize(double(frames.load()) / sr);
    }

    std::vector<Note> snapshot() {
        std::lock_guard<std::mutex> lock(notesMutex);
        auto out = notes;
        if (hasActive && active.end - active.start >= .06) out.push_back(active);
        return out;
    }

    size_t noteCount() {
        std::lock_guard<std::mutex> lock(notesMutex);
        return notes.size() + (hasActive && active.end - active.start >= .06 ? 1 : 0);
    }

    void clearNotes() {
        std::lock_guard<std::mutex> lock(notesMutex);
        notes.clear();
        hasActive = false;
        candidate = -1;
        candidateCount = 0;
    }

    static void le16(FILE* f, uint16_t v) {
        uint8_t b[] = {uint8_t(v), uint8_t(v >> 8)};
        fwrite(b, 1, 2, f);
    }

    static void le32(FILE* f, uint32_t v) {
        uint8_t b[] = {uint8_t(v), uint8_t(v >> 8), uint8_t(v >> 16), uint8_t(v >> 24)};
        fwrite(b, 1, 4, f);
    }

    void wavHeader(FILE* f, uint32_t bytes) {
        fseek(f, 0, SEEK_SET);
        fwrite("RIFF", 1, 4, f);
        le32(f, bytes + 36);
        fwrite("WAVEfmt ", 1, 8, f);
        le32(f, 16);
        le16(f, 1);
        le16(f, 2);
        le32(f, sr);
        le32(f, sr * 4);
        le16(f, 4);
        le16(f, 16);
        fwrite("data", 1, 4, f);
        le32(f, bytes);
    }

    void cacheLocalDiagnostics(){diagnostic[9]=localSource->underruns();diagnostic[10]=localSource->readyFrames();diagnostic[12]=localSource->ioErrors();diagnostic[13]=localSource->seekGeneration();}
    bool reclaimLocal(){
        if(!retiredLocalSource)return true;
        if(!retiredLocalSource->readerStopped())return false;
        retiredLocalSource.reset();return true;
    }
    void retireLocal(std::unique_ptr<LocalPcmSource> source){
        source->requestClose();if(!source->readerStopped())retiredLocalSource=std::move(source);
    }
    // Caller holds localControl. The old source is detached only after its
    // callback stops reading it; its cancelled worker never joins this caller.
    void commitLocal(std::unique_ptr<LocalPcmSource> candidate,int rate){
        localPlaying=false;while(localBusy.load())std::this_thread::yield();
        auto old=std::move(localSource);localSource=std::move(candidate);retireLocal(std::move(old));
        localRate=rate;localDurationSeconds=double(localSource->frames())/rate;
        localCursor=0;localMissingFrames=0;localSeek.clear();localDesiredPlay=false;cacheLocalDiagnostics();
    }
    bool loadLocalFile(const std::string& path,int rate,int channels){
        if(rate<8000||rate>192000||channels<1||channels>2)return false;
        uint64_t epoch;
        {std::lock_guard<std::mutex> lock(localControl);if(localLoadInProgress||!reclaimLocal())return false;localLoadInProgress=true;epoch=localLoadEpoch.fetch_add(1)+1;}
        std::unique_ptr<LocalPcmSource> candidate;bool loaded=false;
        try{candidate=std::make_unique<LocalPcmSource>();loaded=candidate->openPcm16(path.c_str(),rate,channels);}catch(...){loaded=false;}
        std::lock_guard<std::mutex> lock(localControl);localLoadInProgress=false;
        if(!loaded||epoch!=localLoadEpoch.load()) {if(candidate)retireLocal(std::move(candidate));return false;}
        commitLocal(std::move(candidate),rate);return true;
    }
    bool loadLocalMemory(std::vector<float> samples,int rate){
        if(rate<8000||rate>192000||samples.size()<4||samples.size()%2)return false;
        std::lock_guard<std::mutex> lock(localControl);if(localLoadInProgress||!reclaimLocal())return false;
        auto candidate=std::make_unique<LocalPcmSource>();candidate->loadMemory(std::move(samples));localLoadEpoch.fetch_add(1);commitLocal(std::move(candidate),rate);return true;
    }
    void playLocal(bool play){
        localDesiredPlay=play;
        if(!play){localPlaying=false;std::unique_lock<std::mutex> lock(localControl,std::try_to_lock);if(lock.owns_lock())localSource->idle();return;}
        std::lock_guard<std::mutex> lock(localControl);if(localSource->empty())return;
        localPlaying=false;while(localBusy.load())std::this_thread::yield();
        double target=localSeek.consume();if(target>=0){localCursor=uint64_t(target*sr);localSource->seek(uint64_t(target*localRate));}
        if(double(localCursor.load())*localRate/sr>=localSource->frames()-1){localCursor=0;localSource->seek(0);}
        bool ready=localSource->prime(uint64_t(double(localCursor.load())*localRate/sr));
        if(!ready||!localDesiredPlay.load()){localSource->idle();return;}
        params[13]=0;localPlaying=true;
    }
    void seekLocal(double seconds){
        localSeek.request(seconds,localDurationSeconds.load());
        if(!running||!localPlaying.load()){
            std::unique_lock<std::mutex> lock(localControl,std::try_to_lock);if(!lock.owns_lock()||localPlaying.load())return;
            while(localBusy.load())std::this_thread::yield();double target=localSeek.consume();
            if(target>=0){localCursor=uint64_t(target*sr);localSource->seek(uint64_t(target*localRate));}
        }
    }

    bool beginRecord(const std::string& path, const std::string& micPath = "", const std::string& fixedPath = "", const std::string& vocalPath = "") {
        std::lock_guard<std::mutex> recordLock(recordControl);
        if (!running || recording) return false;
        FILE* f = fopen(path.c_str(), "wb");
        if (!f) return false;
        FILE* raw = micPath.empty() ? nullptr : fopen(micPath.c_str(), "wb");
        FILE* fixed = fixedPath.empty() ? nullptr : fopen(fixedPath.c_str(), "wb");
        FILE* vocal = vocalPath.empty() ? nullptr : fopen(vocalPath.c_str(), "wb");
        vocalStemError = !vocal;
        if(vocal) sourceHeader(vocal,sr,2,0);
        sourceError = !micPath.empty() && (!raw || !fixed);
        if (sourceError) { if(raw) fclose(raw); if(fixed) fclose(fixed); raw=fixed=nullptr; }
        if(raw) sourceHeader(raw,sr,1,0);
        if(fixed) sourceHeader(fixed,sr,2,0);
        sourceRing.reset(); sourceRecording = raw && fixed; sourceEditable = sr>=8000 && sr<=192000;
        alignment.init(sr, sessionRecordBus.load()?0:recordSyncMs);
        wavHeader(f, 0);
        recordRing.reset();
        recordPaused = false;
        segmentPending = true;
        segmentOutputStart = 0;
        recordedSpans.clear();
        recordFrames = 0;
        recordStartClock = 0;
        overflow = false;
        diskError=false;writerDroppedFrames=0;writerIoErrors=0;localRecordingMissingFrames=0;
        recordStartTime = double(frames.load()) / sr;
        writerRunning = true;
        recording = true;
        writer=std::thread([this,f,raw,fixed,vocal,path]{
            std::array<float,8192> data{};std::array<int16_t,8192> pcm{};
            std::array<float,20480> sources{};std::array<float,4096> mono{};std::array<float,8192> bed{},voice{};
            uint64_t mixSamples=0,rawSamples=0,fixedSamples=0,vocalSamples=0,sourceFrames=0;
            uint64_t mixDurable=0,rawDurable=0,fixedDurable=0,vocalDurable=0;
            auto error=[this]{writerIoErrors.fetch_add(1,std::memory_order_relaxed);};
            auto durable=[&](FILE* stream,uint64_t frames,uint64_t& confirmed){
                if(!stream)return true;
                if(fflush(stream)!=0||ferror(stream)||fdatasync(fileno(stream))!=0){error();return false;}
                confirmed=frames;return true;
            };
            auto checkpoint=[&](bool final){
                bool ok=durable(f,mixSamples/2,mixDurable);
                bool rawOk=durable(raw,rawSamples,rawDurable),fixedOk=durable(fixed,fixedSamples/2,fixedDurable),vocalOk=durable(vocal,vocalSamples/2,vocalDurable);
                if(!ok)diskError=true;if(!rawOk||!fixedOk)sourceError=true;if(!vocalOk)vocalStemError=true;
                std::vector<recording::JournalStream> streams={{"mix",sr,2,16,1,mixDurable}};
                if(raw)streams.push_back({"raw",sr,1,32,3,rawDurable});if(fixed)streams.push_back({"fixed",sr,2,32,3,fixedDurable});if(vocal)streams.push_back({"vocal",sr,2,32,3,vocalDurable});
                if(!recording::RecordJournal::write(path+".journal",final&&ok&&rawOk&&fixedOk&&vocalOk,streams)){diskError=true;error();}
            };
            checkpoint(false);auto nextCheckpoint=std::chrono::steady_clock::now()+std::chrono::seconds(1);
            while(writerRunning||recordRing.available()||sourceRing.available()){
                size_t n=recordRing.pop(data.data(),data.size());
                if(n){
                    uint64_t maximumSamples=(uint64_t(UINT32_MAX)-36)/4*2;
                    size_t allowed=size_t(std::min<uint64_t>(n,maximumSamples-mixSamples));allowed-=allowed%2;
                    for(size_t i=0;i<allowed;i++)pcm[i]=std::lround(limit(data[i])*32767);
                    size_t written=fwrite(pcm.data(),2,allowed,f);mixSamples+=written;
                    if(written!=n){diskError=true;error();writerDroppedFrames.fetch_add((n-written+1)/2,std::memory_order_relaxed);}
                }
                size_t available=sourceRing.available(),count=sourceRing.pop(sources.data(),std::min(available-available%5,sources.size()));
                if(count&&raw&&fixed){
                    size_t frames=count/5;
                    if(!sourceStorageFits(sourceFrames+frames)){sourceError=true;sourceRecording=false;error();}
                    for(size_t i=0;i<frames;i++){mono[i]=sources[i*5];bed[i*2]=sources[i*5+1];bed[i*2+1]=sources[i*5+2];voice[i*2]=sources[i*5+3];voice[i*2+1]=sources[i*5+4];}
                    if(!sourceError){
                        size_t rawWritten=fwrite(mono.data(),4,frames,raw);rawSamples+=rawWritten;
                        size_t fixedWritten=fwrite(bed.data(),4,frames*2,fixed);fixedSamples+=fixedWritten;
                        if(rawWritten!=frames||fixedWritten!=frames*2){sourceError=true;sourceRecording=false;error();}
                        if(vocal&&!vocalStemError){size_t written=fwrite(voice.data(),4,frames*2,vocal);vocalSamples+=written;if(written!=frames*2){vocalStemError=true;error();}}
                        sourceFrames+=frames;
                    }
                }
                if(std::chrono::steady_clock::now()>=nextCheckpoint){checkpoint(false);nextCheckpoint=std::chrono::steady_clock::now()+std::chrono::seconds(1);}
                if(!n&&!count)std::this_thread::sleep_for(std::chrono::milliseconds(4));
            }
            auto truncate=[&](FILE* stream,uint64_t samples,int sampleBytes,int channels){
                if(!stream)return true;
                if(fflush(stream)!=0||ftruncate(fileno(stream),off_t(44+(samples/channels)*channels*sampleBytes))!=0){error();return false;}return true;
            };
            if(!truncate(f,mixSamples,2,2))diskError=true;wavHeader(f,uint32_t(mixSamples/2*4));
            if(raw){if(!truncate(raw,rawSamples,4,1))sourceError=true;sourceHeader(raw,sr,1,uint32_t(rawSamples*4));}
            if(fixed){if(!truncate(fixed,fixedSamples,4,2))sourceError=true;sourceHeader(fixed,sr,2,uint32_t(fixedSamples/2*8));}
            if(vocal){if(!truncate(vocal,vocalSamples,4,2))vocalStemError=true;sourceHeader(vocal,sr,2,uint32_t(vocalSamples/2*8));}
            checkpoint(!diskError&&(!raw||!sourceError)&&(!fixed||!sourceError)&&(!vocal||!vocalStemError));
            if(raw&&fclose(raw)!=0){sourceError=true;error();}if(fixed&&fclose(fixed)!=0){sourceError=true;error();}if(vocal&&fclose(vocal)!=0){vocalStemError=true;error();}if(fclose(f)!=0){diskError=true;error();}
        });
        return true;
    }

    void closeRecordedSpan() {
        uint64_t count = recordFrames.load() - segmentOutputStart;
        if (count) recordedSpans.push_back({double(segmentSourceStart.load()) / sr, double(count) / sr});
        segmentOutputStart = recordFrames.load();
    }

    void pauseRecord(bool pause) {
        std::lock_guard<std::mutex> recordLock(recordControl);
        if (!recording || recordPaused.load() == pause) return;
        if (pause) {
            recordPaused = true;
            while (recordWriters.load() > 0) std::this_thread::yield();
            closeRecordedSpan();
        } else {
            segmentPending = true;
            recordPaused = false;
        }
    }

    void stopRecord() {
        std::lock_guard<std::mutex> recordLock(recordControl);
        bool wasRecording = recording.exchange(false);
        while (recordWriters.load() > 0) std::this_thread::yield();
        if (wasRecording && !recordPaused) closeRecordedSpan();
        recordPaused = false;
        if (wasRecording) {
            auto tailDeadline=std::chrono::steady_clock::now()+std::chrono::milliseconds(250);
            for (int i = 0; i < alignment.tail(); i++) {
                float vl = 0, vr = 0, ml = 0, mr = 0;
                alignment.tick(vl, vr, ml, mr);
                float pair[] = {limit(vl + ml), limit(vr + mr)};
                bool admitted=false;
                while(!(admitted=recordRing.push(pair,2)==2)&&writerRunning&&std::chrono::steady_clock::now()<tailDeadline)std::this_thread::sleep_for(std::chrono::milliseconds(1));
                if(!admitted){overflow=true;writerDroppedFrames.fetch_add(alignment.tail()-i);break;}
                recordFrames++;
            }
        }
        sourceRecording = false;
        writerRunning = false;
        if (writer.joinable()) writer.join();
    }

    bool exportMidi(const std::string& path, int bpm, bool onlyRecord) {
        auto data = snapshot();
        if (onlyRecord) {
            data = recordedNotes(data, recordedSpans);
            for (auto& n : data) {
                n.start += double(alignment.vocalFrames) / sr;
                n.end += double(alignment.vocalFrames) / sr;
            }
        }
        auto bytes = midiFile(data, bpm);
        FILE* f = fopen(path.c_str(), "wb");
        if (!f) return false;
        bool ok = fwrite(bytes.data(), 1, bytes.size(), f) == bytes.size();
        fclose(f);
        return ok;
    }
};

static Engine engine;

static std::string str(JNIEnv* env, jstring s) {
    const char* p = env->GetStringUTFChars(s, nullptr);
    std::string out(p);
    env->ReleaseStringUTFChars(s, p);
    return out;
}

#define JNI(name) extern "C" JNIEXPORT
static std::atomic<bool> offlineCancel{false};

JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_start(JNIEnv*, jclass, jint d, jint out, jint mode) {
    return engine.start(d, out, mode);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_stop(JNIEnv*, jclass) {
    engine.recentVoice.configure(false);
    offlineCancel=true;
    engine.stop();
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_suspend(JNIEnv*, jclass) {
    engine.stop(true);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_set(JNIEnv*, jclass, jint k, jfloat v) {
    if (k >= 0 && k < 20 && std::isfinite(v)) engine.params[k] = v;
}
JNI(jfloatArray) jfloatArray JNICALL Java_pro_livemic_studio_NativeAudio_stats(JNIEnv* e, jclass) {
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
    engine.refreshOboeSnapshots();
#endif
    float f = engine.frequency;
    float mf = f > 0 ? 69 + 12 * std::log2(f / 440) : 0;
    float values[] = {
        f,
        f > 0 ? (mf - std::round(mf)) * 100 : 0,
        f > 0 ? std::round(mf) : -1,
        engine.inRms,
        engine.outRms,
        engine.beat,
        float(engine.sr),
        1000.f*engine.outputBufferSnapshot.load()/engine.outputRateSnapshot.load(),
        float(double(engine.recordFrames.load()) / engine.sr),
        float(engine.outputXrunsSnapshot.load()),
        engine.running ? 1.f : 0,
        engine.recording ? 1.f : 0,
        float(engine.noteCount()),
        engine.overflow || engine.diskError || engine.localRecordingMissingFrames.load()>0 ? 1.f : 0,
        float(engine.fault),
        float(double(engine.frames.load()) / engine.sr),
        engine.localPlaying ? 1.f : 0,
        engine.feedback.muted() ? 1.f : 0,
        float(engine.feedback.count()),
        float(engine.monitorMode),
        engine.monitorLevel,
        engine.params[11]
    };
    auto out = e->NewFloatArray(22);
    e->SetFloatArrayRegion(out, 0, 22, values);
    return out;
}
JNI(jfloatArray) jfloatArray JNICALL Java_pro_livemic_studio_NativeAudio_roll(JNIEnv* e, jclass) {
    auto data = engine.snapshot();
    if (data.size() > 240) data.erase(data.begin(), data.end() - 240);
    std::vector<float> values;
    for (auto n : data) {
        values.push_back(n.start);
        values.push_back(n.end - n.start);
        values.push_back(n.midi);
    }
    auto out = e->NewFloatArray(values.size());
    if (!values.empty()) e->SetFloatArrayRegion(out, 0, values.size(), values.data());
    return out;
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_beginRecord(JNIEnv* e, jclass, jstring p) {
    return engine.beginRecord(str(e, p));
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_pauseRecord(JNIEnv*, jclass, jboolean pause) {
    engine.pauseRecord(pause);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_endRecord(JNIEnv*, jclass) {
    engine.stopRecord();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_exportMidi(JNIEnv* e, jclass, jstring p, jint bpm, jboolean rec) {
    return engine.exportMidi(str(e, p), bpm, rec);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_clearNotes(JNIEnv*, jclass) {
    engine.clearNotes();
}
JNI(jdouble) jdouble JNICALL Java_pro_livemic_studio_NativeAudio_recordClock(JNIEnv*, jclass) {
    return engine.recordStartClock;
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_loadLocal(JNIEnv* e,jclass,jfloatArray pcm,jint rate){
    if(!pcm||rate<8000||rate>192000)return;int n=e->GetArrayLength(pcm);if(n<4||n%2)return;
    try{std::vector<float> samples(size_t(n),0.f);e->GetFloatArrayRegion(pcm,0,n,samples.data());engine.loadLocalMemory(std::move(samples),rate);}catch(...){return;}
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_playLocal(JNIEnv*,jclass,jboolean play){engine.playLocal(play);}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_loadLocalFile(JNIEnv* e,jclass,jstring path,jint rate,jint channels){return engine.loadLocalFile(str(e,path),rate,channels);}

JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_enablePlaybackCapture(JNIEnv*, jclass, jboolean enable, jfloat gain) {
    engine.playback.configure(enable, gain);
}
JNI(jint) jint JNICALL Java_pro_livemic_studio_NativeAudio_pushPlayback(JNIEnv* e, jclass, jfloatArray data, jint count) {
    if (count <= 0) return 0;
    count = std::min(count, e->GetArrayLength(data));
    count = std::min(count, 8192);
    count -= count % 2;
    std::array<float, 8192> buffer{};
    e->GetFloatArrayRegion(data, 0, count, buffer.data());
    size_t n = engine.playback.push(buffer.data(), count);
    if (n < size_t(count) && engine.recording) engine.overflow = true;
    return n;
}

JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_configureEchoInput(
        JNIEnv* env, jclass, jboolean enabled, jintArray speakers, jintArray microphones, jboolean defaultSafe) {
    EchoInputProfile config;
    config.enabled = enabled;
    config.defaultInputSafe = defaultSafe;
    auto readIds = [env](jintArray values) {
        std::vector<int> ids;
        if (values) {
            ids.resize(env->GetArrayLength(values));
            if (!ids.empty()) env->GetIntArrayRegion(values, 0, ids.size(), ids.data());
        }
        return ids;
    };
    config.speakers = readIds(speakers);
    config.microphones = readIds(microphones);
    std::lock_guard<std::mutex> lock(engine.echoInputControl);
    engine.echoInputConfig = std::move(config);
}
JNI(jint) jint JNICALL Java_pro_livemic_studio_NativeAudio_inputDevice(JNIEnv*, jclass) {
    return engine.inputDeviceSnapshot.load();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_beginRecordSources(JNIEnv* env,jclass,jstring mix,jstring raw,jstring fixed) {
    return engine.beginRecord(str(env,mix),str(env,raw),str(env,fixed));
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_beginRecordStems(JNIEnv* env,jclass,jstring mix,jstring raw,jstring fixed,jstring vocal) {
    return engine.beginRecord(str(env,mix),str(env,raw),str(env,fixed),str(env,vocal));
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_recordVocalStemValid(JNIEnv*,jclass) {
    return !engine.sourceError.load() && !engine.vocalStemError.load();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_recordSourcesValid(JNIEnv*,jclass) {
    return !engine.sourceError.load();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_recordSourcesEditable(JNIEnv*,jclass) {
    return engine.sourceEditable.load();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_renderTakeSources(JNIEnv* env,jclass,jstring raw,jstring fixed,jstring target,jfloatArray values,jint sync) {
    if(env->GetArrayLength(values)!=20)return false;
    float params[20];env->GetFloatArrayRegion(values,0,20,params);for(float value:params)if(!std::isfinite(value))return false;
    offlineCancel=false;return renderSourceTake(str(env,raw),str(env,fixed),str(env,target),params,sync,offlineCancel);
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_renderVocalStem(JNIEnv* env,jclass,jstring vocal,jstring fixed,jstring target,jfloatArray values,jint sync) {
    if(!values || env->GetArrayLength(values)!=20)return false;
    float params[20];env->GetFloatArrayRegion(values,0,20,params);
    offlineCancel=false;return renderVocalStem(str(env,vocal),str(env,fixed),str(env,target),params,sync,offlineCancel);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_cancelTakeRender(JNIEnv*,jclass){offlineCancel=true;}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_recentVoiceEnabled(JNIEnv*,jclass,jboolean on){engine.recentVoice.configure(on);}
JNI(jint) jint JNICALL Java_pro_livemic_studio_NativeAudio_recentVoiceSeconds(JNIEnv*,jclass){return engine.recentVoice.seconds();}
JNI(jlong) jlong JNICALL Java_pro_livemic_studio_NativeAudio_saveRecentVoice(JNIEnv* env,jclass,jstring target){
    uint64_t token=0;return saveRecentVoiceFile(engine.recentVoice,str(env,target),false,&token) ? jlong(token) : -1;
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_commitRecentVoice(JNIEnv*,jclass,jlong token){
    return token>=0 && engine.recentVoice.commitSnapshot(uint64_t(token));
}

JNI(jint) jint JNICALL Java_pro_livemic_studio_NativeAudio_inputProfile(JNIEnv*, jclass) {
    return engine.inputProfile.load();
}

JNI(jint) jint JNICALL Java_pro_livemic_studio_NativeAudio_inputSession(JNIEnv*, jclass) {
    return engine.inputSessionSnapshot.load();
}
JNI(jint) jint JNICALL Java_pro_livemic_studio_NativeAudio_outputDevice(JNIEnv*, jclass) {
    return engine.outputDeviceSnapshot.load();
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_feedbackProtection(JNIEnv*, jclass, jboolean risky) {
    bool changed = engine.feedback.isEnabled() != bool(risky);
    bool wasMuted = engine.feedback.muted();
    if (risky && changed) engine.monitorLevel = std::min(engine.monitorLevel.load(), .18f);
    engine.feedback.setEnabled(risky);
    // Only restore the initial headphone default before a user has selected a
    // level. Route changes must not override a chosen gain or a safety latch.
    if (!risky && changed && !wasMuted && !engine.monitorLevelChosen.load()) engine.monitorLevel = 1.f;
}
JNI(jfloat) jfloat JNICALL Java_pro_livemic_studio_NativeAudio_feedbackReductionDb(JNIEnv*, jclass) {
    return engine.feedback.isEnabled() ? engine.feedback.attenuationDb() : 0.f;
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_monitorLevel(JNIEnv*, jclass, jfloat level) {
    if (!std::isfinite(level)) return;
    engine.monitorLevelChosen = true;
    engine.monitorLevel = std::clamp(level, 0.f, engine.feedback.isEnabled() ? .65f : 1.f);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_resetFeedback(JNIEnv*, jclass) {
    engine.monitorLevel = engine.feedback.isEnabled() ? .18f : std::min(engine.monitorLevel.load(), .18f);
    engine.monitorLevelChosen = true;
    engine.feedback.reset();
}

JNI(jintArray) jintArray JNICALL Java_pro_livemic_studio_NativeAudio_latencyInfo(JNIEnv* e, jclass) {
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
    engine.refreshOboeSnapshots();
#endif
    int v[]={engine.outputBurstSnapshot.load(),engine.outputCapacitySnapshot.load(),engine.outputBufferSnapshot.load(),engine.outputRateSnapshot.load(),engine.outputXrunsSnapshot.load()};
    auto a = e->NewIntArray(5);
    e->SetIntArrayRegion(a, 0, 5, v);
    return a;
}
JNI(jdoubleArray) jdoubleArray JNICALL Java_pro_livemic_studio_NativeAudio_diagnostics(JNIEnv* env,jclass){
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
    engine.refreshOboeSnapshots();
#endif
    double values[21];for(size_t i=0;i<21;i++)values[i]=engine.diagnostic[i].load(std::memory_order_relaxed);
    values[14]=double(engine.writerDroppedFrames.load());values[15]=double(engine.writerIoErrors.load());values[16]=double(engine.localMissingFrames.load());
    auto result=env->NewDoubleArray(21);env->SetDoubleArrayRegion(result,0,21,values);return result;
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_bufferBursts(JNIEnv*, jclass, jint bursts) {
    std::lock_guard<std::recursive_mutex> control(engine.streamControl);
    bursts = std::clamp(int(bursts), 1, 8);
    if (engine.recording) return false;
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
    if(engine.usingOboe()){
        if(!engine.oboe->setOutputBufferBursts(bursts))return false;
        engine.requestedBursts=bursts;engine.refreshOboeSnapshots();return true;
    }
#endif
    if (engine.output) {
        int n = std::min(AAudioStream_getBufferCapacityInFrames(engine.output), AAudioStream_getFramesPerBurst(engine.output) * bursts);
        if (AAudioStream_setBufferSizeInFrames(engine.output, n) < 0) return false;
    }
    engine.requestedBursts = bursts;
    return true;
}
#if defined(__ANDROID__) || defined(LV_TEST_OBOE)
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_requestBackend(JNIEnv*,jclass,jint provider){
    std::lock_guard<std::recursive_mutex> control(engine.streamControl);
    if(provider!=0&&provider!=1)return false;
    if(engine.running || engine.output || engine.input || engine.oboe->hasOutput() || engine.oboe->hasInput() ||
            engine.recording || engine.writerRunning || engine.sourceRecording || engine.playback.isEnabled())return false;
    engine.requestedBackend=provider;
    return true;
}
JNI(jintArray) jintArray JNICALL Java_pro_livemic_studio_NativeAudio_backendInfo(JNIEnv* env,jclass){
    int values[]={engine.requestedBackend.load(),engine.appliedBackend.load(),engine.platformApi.load(),engine.backendFallback.load()};
    auto result=env->NewIntArray(4);env->SetIntArrayRegion(result,0,4,values);return result;
}
#endif
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_recordSync(JNIEnv*, jclass, jint ms) {
    if (!engine.recording) engine.recordSyncMs = std::clamp(int(ms), -500, 500);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_guideTone(JNIEnv*, jclass, jfloat f) {
    engine.guideFrequency = std::isfinite(f) && f >= 75 && f <= 1100 ? f : 0;
}
JNI(jfloatArray) jfloatArray JNICALL Java_pro_livemic_studio_NativeAudio_pitchFrame(JNIEnv* e, jclass) {
    float v[] = {engine.frequency, engine.pitchConfidence, engine.pitchLevel, float(double(engine.frames.load()) / engine.sr)};
    auto a = e->NewFloatArray(4);
    e->SetFloatArrayRegion(a, 0, 4, v);
    return a;
}

JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_seekLocal(JNIEnv*,jclass,jdouble seconds){engine.seekLocal(seconds);}
JNI(jdoubleArray) jdoubleArray JNICALL Java_pro_livemic_studio_NativeAudio_localPlaybackInfo(JNIEnv* e, jclass) {
    double target = engine.localSeek.peek();
    double values[] = {target >= 0 ? target : double(engine.localCursor.load()) / engine.sr, engine.localDurationSeconds.load()};
    auto a = e->NewDoubleArray(2);
    e->SetDoubleArrayRegion(a, 0, 2, values);
    return a;
}

JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_reroute(JNIEnv*, jclass, jint input, jint out, jint mode, jboolean mic) {
    std::lock_guard<std::recursive_mutex> control(engine.streamControl);
    engine.stop(true);
    return engine.start(input, out, mode, mic, true);
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeAudio_startPlayback(JNIEnv*, jclass, jint out, jint mode) {
    return engine.start(0, out, mode, false);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_microphoneOff(JNIEnv*, jclass) {
    engine.microphoneOff();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeComposer_loadSoundFont(JNIEnv* e, jclass, jstring path) {
    return engine.composer.load(str(e, path).c_str());
}
JNI(jobjectArray) jobjectArray JNICALL Java_pro_livemic_studio_NativeComposer_drumKits(JNIEnv* e, jclass) {
    auto kits = engine.composer.kits();
    auto a = e->NewObjectArray(kits.size(), e->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < kits.size(); i++) {
        auto s = e->NewStringUTF(kits[i].c_str());
        e->SetObjectArrayElement(a, i, s);
        e->DeleteLocalRef(s);
    }
    return a;
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeComposer_publish(JNIEnv* e, jclass, jdoubleArray data, jdouble length, jdouble bar, jdouble bpm, jint key, jint scale) {
    int count = e->GetArrayLength(data);
    if (count % 9 || count > 3000000 || length <= 0 || length > 1024 || bar < 1 || bar > 4 || bpm < 40 || bpm > 360 || key < 0 || key > 11 || scale < 0 || scale > 4) {
        return false;
    }
    std::vector<double> values(count);
    e->GetDoubleArrayRegion(data, 0, count, values.data());
    std::vector<lv::Event> events;
    events.reserve(count / 9);
    for (int i = 0; i < count; i += 9) {
        for (int j = 0; j < 9; j++) if (!std::isfinite(values[i + j])) return false;
        if (values[i] < 0 || values[i + 2] < 0 || values[i + 2] > 7 || values[i + 1] < 0 || values[i + 1] > 4) return false;
        events.push_back({values[i], int(values[i + 1]), int(values[i + 2]), int(values[i + 3]), float(values[i + 4]), int(values[i + 5]), int(values[i + 6]), float(values[i + 7]), float(values[i + 8])});
    }
    return engine.composer.publish(events, length, bar, bpm, key, scale);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeComposer_transport(JNIEnv*, jclass, jint c) {
    if (c >= 0 && c <= 3) engine.composer.transport(c);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeComposer_clearSolo(JNIEnv*, jclass) {
    engine.composer.clearSolo();
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeComposer_track(JNIEnv*, jclass, jint ch, jfloat v, jfloat p, jboolean mute, jboolean solo) {
    engine.composer.track(ch, std::clamp(v, 0.f, 1.f), std::clamp(p, -1.f, 1.f), mute, solo);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeComposer_voice(JNIEnv*, jclass, jboolean enabled, jint program, jint mode, jint octave, jboolean range, jboolean snap, jint response, jint quality, jboolean rhythm, jboolean mix, jfloat confidence, jfloat gate) {
    engine.composer.settings(enabled, std::clamp(int(program), 0, 127), mode == 1 ? 1 : 0, std::clamp(int(octave), -3, 3), range, snap, response == 1 ? 1 : 0, std::clamp(int(quality), -1, 10), rhythm, mix, std::clamp(confidence, .5f, .99f), std::clamp(gate, .001f, .1f));
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeComposer_monitor(JNIEnv*, jclass, jboolean enabled) {
    engine.composer.monitor(enabled);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeComposer_record(JNIEnv*, jclass, jboolean arm) {
    engine.composer.record(arm);
}
JNI(jdoubleArray) jdoubleArray JNICALL Java_pro_livemic_studio_NativeComposer_state(JNIEnv* e, jclass) {
    auto& c = engine.composer;
    double values[] = {c.beat.load(), c.playing ? 1. : 0., double(c.part.load()), c.partStart.load(), c.partEnd.load(), double(c.inputNote.load()), double(c.outputNote.load()), c.level.load(), double(c.recording.load()), c.overflow ? 1. : 0.};
    auto a = e->NewDoubleArray(10);
    e->SetDoubleArrayRegion(a, 0, 10, values);
    return a;
}
JNI(jdoubleArray) jdoubleArray JNICALL Java_pro_livemic_studio_NativeComposer_drain(JNIEnv* e, jclass) {
    std::vector<double> values;
    lv::Performance p;
    while (engine.composer.performance.pop(p)) {
        values.insert(values.end(), {p.beat, double(p.kind), double(p.midi), double(p.velocity), p.cents, p.expression, double(p.degree), double(p.quality)});
    }
    auto a = e->NewDoubleArray(values.size());
    if (!values.empty()) e->SetDoubleArrayRegion(a, 0, values.size(), values.data());
    return a;
}

JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeHarmony_prepare(JNIEnv*, jclass, jint bpm, jint meter, jint bars) {
    std::lock_guard<std::mutex> lock(engine.vocalControl);
    return engine.vocalLooper.prepare(bpm, meter, bars);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeHarmony_configure(JNIEnv*, jclass, jboolean enabled, jint key, jint scale, jint preset, jfloat wet, jfloat dry, jfloat spread) {
    if (key < 0 || key > 11 || scale < 0 || scale > 4 || preset < 0 || preset > 3 || !std::isfinite(wet) || !std::isfinite(dry) || !std::isfinite(spread) || wet < 0 || wet > 1 || dry < 0 || dry > 1 || spread < 0 || spread > 1) return;
    engine.harmony.configure(enabled, key, scale, preset, wet, dry, spread);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeHarmony_monitor(JNIEnv*, jclass, jboolean enabled) {
    engine.vocalMonitor = bool(enabled);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeHarmony_transport(JNIEnv*, jclass, jint command) {
    if (command >= 0 && command <= 2) engine.vocalLooper.transport(command);
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeHarmony_arm(JNIEnv*, jclass, jint slot) {
    return engine.vocalLooper.arm(slot);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeHarmony_cancel(JNIEnv*, jclass) {
    engine.vocalLooper.cancel();
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeHarmony_layer(JNIEnv*, jclass, jint slot, jfloat volume, jfloat pan, jboolean muted) {
    if (slot < 0 || slot > 3 || !std::isfinite(volume) || !std::isfinite(pan) || volume < 0 || volume > 1 || pan < -1 || pan > 1) return;
    engine.vocalLooper.layer(slot, volume, pan, muted);
}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeHarmony_clear(JNIEnv*, jclass, jint slot) {
    engine.vocalLooper.clear(slot);
}
static bool harmonyPath(JNIEnv* e, jstring path, std::string& out) {
    if (!path) return false;
    const char* chars = e->GetStringUTFChars(path, nullptr);
    if (!chars) return false;
    out = chars;
    e->ReleaseStringUTFChars(path, chars);
    return !out.empty();
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeHarmony_saveLayer(JNIEnv* e, jclass, jint slot, jstring path) {
    if (slot < 0 || slot > 3) return false;
    std::string value;
    if (!harmonyPath(e, path, value)) return false;
    return engine.vocalLooper.saveLayer(slot, value.c_str());
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeHarmony_loadLayer(JNIEnv* e, jclass, jint slot, jstring path) {
    if (slot < 0 || slot > 3) return false;
    std::string value;
    if (!harmonyPath(e, path, value)) return false;
    std::lock_guard<std::mutex> lock(engine.vocalControl);
    return engine.vocalLooper.loadLayer(slot, value.c_str());
}
JNI(jdoubleArray) jdoubleArray JNICALL Java_pro_livemic_studio_NativeHarmony_state(JNIEnv* e, jclass) {
    double values[14]{};
    engine.vocalLooper.state(values);
    values[8] = engine.harmony.inputNote.load();
    values[9] = engine.harmony.note1.load();
    values[10] = engine.harmony.note2.load();
    values[11] = engine.harmony.level.load();
    auto a = e->NewDoubleArray(14);
    if (a) e->SetDoubleArrayRegion(a, 0, 14, values);
    return a;
}

JNI(void) void JNICALL Java_pro_livemic_studio_NativeAudio_recordMicOnly(JNIEnv*, jclass, jboolean enabled) {
    if (!engine.recording) engine.recordMicOnly = bool(enabled);
}

JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeSession_prepare(JNIEnv* e,jclass,jobjectArray paths,jlongArray data,jdoubleArray gains,jlong duration,jdouble bpm){
 if(!paths||!data||!gains||duration<0)return false;jsize count=e->GetArrayLength(paths);if(count>32||e->GetArrayLength(data)!=count*7||e->GetArrayLength(gains)!=count*2)return false;
 try{std::vector<jlong> d(size_t(count)*7);std::vector<jdouble> g(size_t(count)*2);e->GetLongArrayRegion(data,0,count*7,d.data());e->GetDoubleArrayRegion(gains,0,count*2,g.data());if(e->ExceptionCheck())return false;
 auto p=std::make_unique<SessionTransport::Plan>();p->duration=uint64_t(duration);p->bpm=bpm;
 for(int i=0;i<count;i++){for(int k=0;k<7;k++)if(d[i*7+k]<0)return false;if(d[i*7]>7||d[i*7+4]>1)return false;SessionTransport::Clip c;c.track=int(d[i*7]);c.start=d[i*7+1];c.offset=d[i*7+2];c.length=d[i*7+3];c.loop=d[i*7+4]!=0;c.loopLength=d[i*7+5];c.gain=float(g[i*2]);c.pan=float(g[i*2+1]);auto path=static_cast<jstring>(e->GetObjectArrayElement(paths,i));if(!path)return false;std::string value=str(e,path);e->DeleteLocalRef(path);c.source=std::make_unique<LocalPcmSource>();if(!c.source->openWav(value.c_str())||c.source->sampleRate()!=d[i*7+6])return false;p->clips.push_back(std::move(c));}
 return engine.session.prepare(std::move(p));}catch(...){return false;}
}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeSession_transport(JNIEnv*,jclass,jint command){return engine.session.transport(command);}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeSession_seek(JNIEnv*,jclass,jlong frame){return frame>=0&&engine.session.seek(uint64_t(frame));}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeSession_setEnabled(JNIEnv*,jclass,jboolean enabled){engine.session.setEnabled(enabled);if(!enabled)engine.composer.transport(0);}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeSession_recordBus(JNIEnv*,jclass,jboolean enabled){engine.sessionRecordBus=enabled;}
JNI(jboolean) jboolean JNICALL Java_pro_livemic_studio_NativeSession_armCapture(JNIEnv*,jclass,jlong token){return engine.session.armCapture(token);}
JNI(void) void JNICALL Java_pro_livemic_studio_NativeSession_finishCapture(JNIEnv*,jclass){engine.session.finishCapture();}
JNI(jlongArray) jlongArray JNICALL Java_pro_livemic_studio_NativeSession_state(JNIEnv* e,jclass){int64_t raw[11]{};engine.session.state(raw);jlong values[11];for(int i=0;i<11;i++)values[i]=raw[i];jlongArray result=e->NewLongArray(11);if(result)e->SetLongArrayRegion(result,0,11,values);return result;}
