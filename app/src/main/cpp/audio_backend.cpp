#include "audio_backend.h"

#include <cstring> // Oboe 1.11.0 FullDuplexStream.h uses memset without including this.
#include <oboe/Oboe.h>
#include <algorithm>
#include <atomic>
#include <thread>
#include <utility>

namespace lv {
namespace {
int32_t code(oboe::Result result) { return static_cast<int32_t>(result); }

class OutputCallback final : public oboe::AudioStreamDataCallback {
public:
    OutputCallback(OboeBackend::Render render, void* user) : render_(render), user_(user) {}
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        render_(user_, static_cast<float*>(data), frames);
        return oboe::DataCallbackResult::Continue;
    }
private:
    OboeBackend::Render render_;
    void* user_;
};

class StreamErrorCallback final : public oboe::AudioStreamErrorCallback {
public:
    StreamErrorCallback(OboeBackend::Error error, void* user) : error_(error), user_(user) {}
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result result) override {
        error_(user_, code(result));
    }
private:
    OboeBackend::Error error_;
    void* user_;
};

OboeBackend::StreamInfo info(const std::shared_ptr<oboe::AudioStream>& stream) {
    OboeBackend::StreamInfo value;
    if (!stream) return value;
    value.deviceId = stream->getDeviceId();
    value.sampleRate = stream->getSampleRate();
    value.channels = stream->getChannelCount();
    value.format = static_cast<int32_t>(stream->getFormat());
    value.inputPreset = static_cast<int32_t>(stream->getInputPreset());
    value.sessionId = static_cast<int32_t>(stream->getSessionId());
    value.framesPerBurst = stream->getFramesPerBurst();
    value.bufferCapacity = stream->getBufferCapacityInFrames();
    value.bufferSize = stream->getBufferSizeInFrames();
    const auto xruns = stream->getXRunCount();
    value.xruns = xruns ? xruns.value() : -1;
    value.platformApi = static_cast<int32_t>(stream->getAudioApi());
    return value;
}

void close(std::shared_ptr<oboe::AudioStream> stream) {
    if (!stream) return;
    stream->requestStop();
    stream->close();
}

int32_t validate(const std::shared_ptr<oboe::AudioStream>& stream, int32_t requestedDevice,
                 int32_t minChannels, int32_t maxChannels) {
    if (!stream) return code(oboe::Result::ErrorInternal);
    const auto value = info(stream);
    if (requestedDevice > 0 && value.deviceId > 0 && value.deviceId != requestedDevice)
        return code(oboe::Result::ErrorUnavailable);
    if (value.format != static_cast<int32_t>(oboe::AudioFormat::Float) ||
            value.channels < minChannels || value.channels > maxChannels || value.sampleRate <= 0 ||
            stream->getAudioApi() != oboe::AudioApi::AAudio)
        return code(oboe::Result::ErrorInvalidFormat);
    return 0;
}
} // namespace

class OboeBackend::Impl {
public:
    Impl(Render render, Error error, void* user)
            : renderCallback(std::make_shared<OutputCallback>(render, user)),
              errorCallback(std::make_shared<StreamErrorCallback>(error, user)), user(user) {}

    std::shared_ptr<oboe::AudioStream> output;
    std::shared_ptr<oboe::AudioStream> input;
    // Callback reads take a raw-pointer lease. The control thread owns the
    // shared_ptr and waits for all leases after unpublishing before close().
    std::atomic<oboe::AudioStream*> publishedOutput{nullptr};
    std::atomic<oboe::AudioStream*> publishedInput{nullptr};
    std::shared_ptr<OutputCallback> renderCallback;
    std::shared_ptr<StreamErrorCallback> errorCallback;
    std::atomic<int32_t> outputReaders{0};
    std::atomic<int32_t> inputReaders{0};
    void* user;
};

OboeBackend::OboeBackend(Render render, Error error, void* user)
        : impl_(std::make_unique<Impl>(render, error, user)) {}
OboeBackend::~OboeBackend() { closeOutput(); closeInput(); }

int32_t OboeBackend::openOutput(int32_t requestedDeviceId, int mode) {
    closeOutput();
    std::shared_ptr<oboe::AudioStream> opened;
    oboe::AudioStreamBuilder builder;
    builder.setAudioApi(oboe::AudioApi::AAudio)
            ->setDirection(oboe::Direction::Output)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(2)
            ->setUsage(mode == 1 ? oboe::Usage::VoiceCommunication : oboe::Usage::Media)
            ->setContentType(mode == 1 ? oboe::ContentType::Speech : oboe::ContentType::Music)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(mode == 2 ? oboe::SharingMode::Shared : oboe::SharingMode::Exclusive)
            ->setDataCallback(impl_->renderCallback)
            ->setErrorCallback(impl_->errorCallback);
    if (requestedDeviceId > 0) builder.setDeviceId(requestedDeviceId);
    auto result = builder.openStream(opened);
    if (result != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(opened);
    }
    if (result != oboe::Result::OK) {
        builder.setPerformanceMode(oboe::PerformanceMode::None);
        result = builder.openStream(opened);
    }
    if (result != oboe::Result::OK) return code(result);
    int32_t valid = validate(opened, requestedDeviceId, 2, 2);
    if (valid) close(opened);
    else {
        impl_->output = std::move(opened);
        impl_->publishedOutput.store(impl_->output.get());
    }
    return valid;
}

int32_t OboeBackend::openInput(int32_t requestedDeviceId, int32_t sampleRate,
                                bool communication, bool speakerEcho, BuiltinMic builtinMic) {
    closeInput();
    std::shared_ptr<oboe::AudioStream> opened;
    oboe::AudioStreamBuilder builder;
    builder.setAudioApi(oboe::AudioApi::AAudio)
            ->setDirection(oboe::Direction::Input)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setSampleRate(sampleRate)
            ->setPerformanceMode(communication ? oboe::PerformanceMode::None : oboe::PerformanceMode::LowLatency)
            ->setInputPreset(communication ? oboe::InputPreset::VoiceCommunication : oboe::InputPreset::VoiceRecognition)
            ->setSessionId(oboe::SessionId::Allocate)
            ->setErrorCallback(impl_->errorCallback);
    if (requestedDeviceId > 0) builder.setDeviceId(requestedDeviceId);
    auto attempt = [&]() {
        auto result = builder.openStream(opened);
        if (result != oboe::Result::OK) {
            builder.setPerformanceMode(oboe::PerformanceMode::None)->setSharingMode(oboe::SharingMode::Shared);
            result = builder.openStream(opened);
        }
        if (result != oboe::Result::OK) {
            builder.setSampleRate(oboe::kUnspecified)->setChannelCount(oboe::kUnspecified);
            result = builder.openStream(opened);
        }
        return result;
    };
    auto result = attempt();
    if (speakerEcho && (result != oboe::Result::OK || !opened || !builtinMic ||
            !builtinMic(impl_->user, opened->getDeviceId()))) {
        close(opened);
        opened.reset();
        builder.setInputPreset(oboe::InputPreset::VoiceRecognition)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency);
        result = attempt();
    }
    if (result != oboe::Result::OK) return code(result);
    int32_t valid = validate(opened, requestedDeviceId, 1, 2);
    if (valid) close(opened);
    else {
        impl_->input = std::move(opened);
    }
    return valid;
}

int32_t OboeBackend::startOutput() {
    return impl_->output ? code(impl_->output->requestStart()) : code(oboe::Result::ErrorInvalidState);
}
int32_t OboeBackend::startInput() {
    if (!impl_->input) return code(oboe::Result::ErrorInvalidState);
    int32_t result = code(impl_->input->requestStart());
    if (result == 0) impl_->publishedInput.store(impl_->input.get());
    return result;
}
void OboeBackend::closeOutput() {
    impl_->publishedOutput.store(nullptr);
    while (impl_->outputReaders.load() != 0) std::this_thread::yield();
    close(std::move(impl_->output));
    impl_->output.reset();
}
void OboeBackend::closeInput() {
    impl_->publishedInput.store(nullptr);
    while (impl_->inputReaders.load() != 0) std::this_thread::yield();
    close(std::move(impl_->input));
    impl_->input.reset();
}
int32_t OboeBackend::readInput(float* buffer, int32_t frames) {
    impl_->inputReaders.fetch_add(1);
    auto* stream = impl_->publishedInput.load();
    int32_t value = code(oboe::Result::ErrorInvalidState);
    if (stream) {
        auto result = stream->read(buffer, frames, 0);
        value = result ? result.value() : code(result.error());
    }
    impl_->inputReaders.fetch_sub(1);
    return value;
}
int32_t OboeBackend::availableInputFrames() const {
    impl_->inputReaders.fetch_add(1);
    auto* stream = impl_->publishedInput.load();
    int32_t value = code(oboe::Result::ErrorInvalidState);
    if (stream) {
        auto result = stream->getAvailableFrames();
        value = result ? static_cast<int32_t>(std::min<int64_t>(result.value(), INT32_MAX)) : code(result.error());
    }
    impl_->inputReaders.fetch_sub(1);
    return value;
}
bool OboeBackend::setOutputBufferBursts(int32_t bursts) {
    impl_->outputReaders.fetch_add(1);
    auto* stream = impl_->publishedOutput.load();
    bool success = false;
    if (stream && bursts >= 1) {
        auto value = info(impl_->output);
        if (value.framesPerBurst > 0 && value.bufferCapacity > 0) {
            int32_t frames = std::min(value.bufferCapacity, value.framesPerBurst * bursts);
            success = bool(stream->setBufferSizeInFrames(frames));
        }
    }
    impl_->outputReaders.fetch_sub(1);
    return success;
}
OboeBackend::StreamInfo OboeBackend::outputInfo() const {
    impl_->outputReaders.fetch_add(1);
    auto value = impl_->publishedOutput.load() ? info(impl_->output) : StreamInfo{};
    impl_->outputReaders.fetch_sub(1);
    return value;
}
OboeBackend::StreamInfo OboeBackend::inputInfo() const {
    impl_->inputReaders.fetch_add(1);
    auto value = impl_->publishedInput.load() ? info(impl_->input) : StreamInfo{};
    impl_->inputReaders.fetch_sub(1);
    return value;
}
bool OboeBackend::hasOutput() const { return impl_->publishedOutput.load() != nullptr; }
bool OboeBackend::hasInput() const { return impl_->publishedInput.load() != nullptr; }
} // namespace lv
