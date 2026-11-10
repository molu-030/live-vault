#pragma once
#include <cstdint>
namespace lm {
// Exact common audio-rate clock. A 48k project frame is 2940 ticks;
// 44.1k and 48k callback clocks never accumulate floating point drift.
struct SessionTimeline {
 static constexpr uint64_t projectRate=48000,tickRate=141120000,ticksPerFrame=2940;
 uint64_t ticks=0,remainder=0;int lastRate=48000;
 uint64_t frame()const{return ticks/ticksPerFrame;}
 double position()const{return double(ticks)/ticksPerFrame;}
 void seek(uint64_t frame){ticks=frame*ticksPerFrame;remainder=0;}
 void advance(int rate){if(rate<=0)return;if(lastRate!=rate){remainder=0;lastRate=rate;}ticks+=tickRate/uint64_t(rate);remainder+=tickRate%uint64_t(rate);if(remainder>=uint64_t(rate)){ticks++;remainder-=rate;}}
};
}
