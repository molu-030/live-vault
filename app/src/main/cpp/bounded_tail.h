#pragma once
#include <cmath>
#include <cstddef>
namespace lm {
// A certificate is valid only while the owner confirms every intervening write
// is quiet. Bounded visits include old, currently unread ring contents.
class BoundedTail {
    size_t span_ = 0, offset_ = 0;
    unsigned cadence_ = 0;
    size_t inspected_ = 0;
public:
    static constexpr float floor = 1e-18f;
    static constexpr size_t budget = 8;
    static constexpr unsigned period = 16;
    static bool quiet(float x) { return std::isfinite(x) && std::abs(x) <= floor; }
    void reset() { span_ = offset_ = 0; }
    void init() { reset(); cadence_ = 0; inspected_ = 0; }
    size_t inspected() const { return inspected_; }
    template<class Span> bool step(bool writesQuiet, size_t count, Span span) {
        if (!writesQuiet) { reset(); return false; }
        if (++cadence_ < period) return false;
        cadence_ = 0;
        for (size_t n = 0; n < budget; ++n) {
            auto values = span(span_);
            ++inspected_;
            if (!quiet(values.first[offset_])) { reset(); return false; }
            if (++offset_ == values.second) {
                offset_ = 0;
                if (++span_ == count) { reset(); return true; }
            }
        }
        return false;
    }
};
}
