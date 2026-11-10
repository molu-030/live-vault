#pragma once

#include <cstdint>
#include <memory>

namespace lv {

// The optional Oboe transport. The existing AAudio transport remains the default.
// Both transports feed the same Engine render and microphone read paths.
class OboeBackend {
public:
    using Render = void (*)(void* user, float* stereo, int32_t frames);
    using Error = void (*)(void* user, int32_t result);
    using BuiltinMic = bool (*)(void* user, int32_t deviceId);

    struct StreamInfo {
        int32_t deviceId = 0;
        int32_t sampleRate = 0;
        int32_t channels = 0;
        int32_t format = 0;
        int32_t inputPreset = 0;
        int32_t sessionId = 0;
        int32_t framesPerBurst = 0;
        int32_t bufferCapacity = 0;
        int32_t bufferSize = 0;
        int32_t xruns = -1;
        int32_t platformApi = 0; // oboe::AudioApi; query the opened stream.
    };

    OboeBackend(Render render, Error error, void* user);
    ~OboeBackend();
    OboeBackend(const OboeBackend&) = delete;
    OboeBackend& operator=(const OboeBackend&) = delete;

    // Returns zero on success; on failure the caller may retry using AAudio.
    int32_t openOutput(int32_t requestedDeviceId, int mode);
    int32_t openInput(int32_t requestedDeviceId, int32_t sampleRate, bool communication,
                      bool speakerEcho, BuiltinMic builtinMic);
    int32_t startOutput();
    int32_t startInput();
    void closeOutput();
    void closeInput();
    int32_t readInput(float* buffer, int32_t frames);
    // Returns queued capture frames, or a negative Oboe error code.
    int32_t availableInputFrames() const;
    bool setOutputBufferBursts(int32_t bursts);
    StreamInfo outputInfo() const;
    StreamInfo inputInfo() const;
    bool hasOutput() const;
    bool hasInput() const;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace lv
