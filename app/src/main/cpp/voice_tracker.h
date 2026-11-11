#pragma once
#include "audio_core.h"
namespace lv {
template<class T,size_t N> class Queue {
    std::array<T,N> data{};std::atomic<uint64_t> read{0},write{0};
public:
    bool push(const T& value){auto w=write.load(std::memory_order_relaxed);if(w-read.load(std::memory_order_acquire)>=N)return false;data[w%N]=value;write.store(w+1,std::memory_order_release);return true;}
    bool pop(T& value){auto r=read.load(std::memory_order_relaxed);if(r==write.load(std::memory_order_acquire))return false;value=data[r%N];read.store(r+1,std::memory_order_release);return true;}
    void clear(){T v;while(pop(v)){};}
};
struct VoiceCommand {int kind=0,input=-1,midi=-1,velocity=80;float cents=0,expression=1;};
class VoiceTracker {
    int current=-1,candidate=-1,count=0,quiet=0;float lastLevel=0,trough=0;int retriggerCooldown=0;
public:
    void reset(){current=candidate=-1;count=quiet=0;lastLevel=trough=0;retriggerCooldown=0;}
    VoiceCommand process(lm::Pitch p,int response,float confidence,float gate){
        VoiceCommand result;result.kind=-1;
        int quietLimit = (response == 0) ? 2 : 5;
        bool nearCurrent = false;
        if (current >= 0 && p.frequency > 0) {
            float pitchCheck = 69.f + 12.f * std::log2(p.frequency / 440.f);
            if (std::abs(pitchCheck - current) < 1.2f) {
                nearCurrent = true;
            }
        }
        float reqConf = nearCurrent ? confidence * 0.82f : confidence;
        float reqGate = nearCurrent ? gate * 0.55f : gate;
        if(p.frequency<=0||p.confidence<reqConf||p.rms<reqGate){
            if(++quiet>=quietLimit&&current>=0){result.kind=0;result.input=current;current=-1;}
            candidate=-1;count=0;lastLevel=p.rms;trough=p.rms;return result;
        }
        quiet=0;float pitch=69+12*std::log2(p.frequency/440);int midi=int(std::lround(pitch));
        // Keep the sounding note through the +/-50-cent rounding boundary during vibrato.
        if(current>=0&&std::abs(pitch-current)<.7f)midi=current;
        if(candidate==midi)count++;else{candidate=midi;count=1;}
        bool repeated=current==midi&&retriggerCooldown<=0&&lastLevel>0&&p.rms>std::max(gate*2,trough*1.8f)&&p.rms>lastLevel*1.25f;
        if(retriggerCooldown>0)retriggerCooldown--;
        result.input=midi;result.midi=midi;result.velocity=std::clamp(int(std::sqrt(p.rms)*230),25,127);result.expression=std::clamp(std::sqrt(p.rms)*3,0.15f,1.f);result.cents=(pitch-midi)*100;
        if((midi!=current&&count>=(response==0?2:3))||repeated){current=midi;result.kind=1;retriggerCooldown=8;trough=p.rms;}
        else if(current>=0){result.kind=2;result.input=current;result.midi=current;result.cents=(pitch-current)*100;}
        lastLevel=p.rms;trough=trough==0?p.rms:std::min(p.rms,trough*1.04f);return result;
    }
};
}
