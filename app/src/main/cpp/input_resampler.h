#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>

namespace lm {
// Callback-owned mono FIFO. Input is downmixed before interpolation. No allocation.
class InputResampler {
    static constexpr size_t capacity = 131072;
    std::array<float, capacity> samples{};
    size_t head = 0, count = 0;
    double phase = 0, ratio = 1;
public:
    void reset(int inputRate, int outputRate) {
        head = count = 0; phase = 0;
        ratio = double(inputRate) / outputRate;
    }
    size_t needed(int outputFrames) const {
        if (outputFrames <= 0) return 0;
        double last = phase + (outputFrames - 1) * ratio;
        size_t required = size_t(std::ceil(last)) + 1;
        return required > count ? std::min(required - count, capacity - count) : 0;
    }
    size_t push(const float* input, size_t frames, int channels) {
        size_t accepted = std::min(frames, capacity - count);
        for (size_t i = 0; i < accepted; ++i)
            samples[(head + count + i) % capacity] = channels == 2
                    ? .5f * (input[i * 2] + input[i * 2 + 1]) : input[i];
        count += accepted;
        return accepted;
    }
    void render(float* output, int frames) {
        int i = 0;
        for (; i < frames; ++i) {
            size_t a = size_t(phase);
            double fraction = phase - a;
            if (a >= count || (fraction > 1e-9 && a + 1 >= count)) break;
            float first = samples[(head + a) % capacity];
            output[i] = fraction <= 1e-9 ? first : float(first +
                    (samples[(head + a + 1) % capacity] - first) * fraction);
            phase += ratio;
        }
        if (i < frames) {
            // Missing input is silence, never a stretched version of a short read.
            std::fill(output + i, output + frames, 0.f);
            head = count = 0; phase = 0;
        } else {
            size_t consumed = std::min(size_t(phase), count);
            head = (head + consumed) % capacity; count -= consumed;
            phase -= consumed;
        }
    }
};
}
