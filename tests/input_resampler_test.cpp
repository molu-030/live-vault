#include "../app/src/main/cpp/input_resampler.h"
#include <cassert>
#include <iostream>
#include <memory>

static float signal(size_t frame, int rate) {
    return float(.3 * std::sin(2 * 3.141592653589793 * 440 * double(frame) / rate));
}
static void continuity(int inputRate, int outputRate, int channels) {
    auto resampler = std::make_unique<lm::InputResampler>();
    resampler->reset(inputRate, outputRate);
    std::array<float, 8192> input{};
    std::array<float, 4096> output{};
    size_t source = 0, destination = 0;
    for (int block = 0; block < 500; ++block) {
        int frames = 1 + (block * 137) % 4096;
        size_t needed;
        while ((needed = resampler->needed(frames))) {
            size_t chunk = std::min<size_t>(needed, 173);
            for (size_t i = 0; i < chunk; ++i)
                for (int c = 0; c < channels; ++c) input[i * channels + c] = signal(source + i, inputRate);
            assert(resampler->push(input.data(), chunk, channels) == chunk);
            source += chunk;
        }
        resampler->render(output.data(), frames);
        for (int i = 0; i < frames; ++i) {
            double position = double(destination + i) * inputRate / outputRate;
            size_t a = size_t(position);
            float expected = float(signal(a, inputRate) + (signal(a + 1, inputRate) - signal(a, inputRate)) * (position - a));
            assert(std::abs(output[i] - expected) < 2e-6);
        }
        destination += frames;
    }
}
static void shortStereoRead() {
    auto r = std::make_unique<lm::InputResampler>();
    r->reset(48000, 48000);
    std::array<float, 384> input{}, output{};
    for (int block = 0; block < 8; ++block) {
        int got = block % 2 ? 192 : 96;
        for (int i = 0; i < got; ++i) { input[2*i] = signal(i,48000); input[2*i+1] = 0; }
        assert(r->push(input.data(), got, 2) == size_t(got));
        r->render(output.data(),192);
        for (int i = 0; i < 192; ++i) assert(output[i] == (i < got ? .5f * signal(i,48000) : 0));
    }
    r->reset(44100,48000);
    float first[]={.4f,.4f,.8f,.8f};
    r->push(first,2,2);r->render(output.data(),192);
    assert(output[0]==.4f);assert(output[1]>.4f && output[1]<.8f);
    for(int i=2;i<192;i++)assert(output[i]==0);
    r->reset(48000,44100);r->render(output.data(),192);
    for(int i=0;i<192;i++)assert(output[i]==0);
}
int main() {
    shortStereoRead();
    for (int channels : {1,2}) {continuity(44100,48000,channels);continuity(48000,44100,channels);continuity(48000,48000,channels);}
    std::cout << "PASS: stereo short reads zero-fill; fixed FIFO 44.1/48kHz continuous fractional conversion and resets\n";
}
