#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include "input_drift.h"

namespace lm {
// Callback-owned mono FIFO. Downmix before interpolation; no callback allocation.
class InputResampler {
    static constexpr size_t capacity=131072;
    std::array<float,capacity> samples{};
    size_t head=0,count=0;
    double phase=0,ratio=1,renderPpm=0;
    int inputRate=48000,outputRate=48000;
    bool adaptive=false,primed=false;
    InputDrift drift;
    uint64_t missing=0,dropped=0,starvations=0,warmingSilence=0;
    int warmupFrames()const{return std::max(1,int(std::lround(outputRate*.008)));}
    void rePrime(){head=count=0;phase=renderPpm=0;primed=false;drift.reset(inputRate,adaptive?double(warmupFrames())*ratio:0);}
public:
    // Reopen/rate/route changes reset FIFO, phase, servo and per-stream counters.
    // Adaptive mode adds ~8ms input buffering. Priming can finish inside a
    // callback; the first captured samples are retained, not discarded/repeated.
    void reset(int inRate,int outRate,bool adapt=false){
        inputRate=std::max(1,inRate);outputRate=std::max(1,outRate);ratio=double(inputRate)/outputRate;adaptive=adapt;
        missing=dropped=starvations=warmingSilence=0;rePrime();
    }
    size_t needed(int outputFrames)const{
        if(outputFrames<=0)return 0;
        double step=ratio*(1+(adaptive?std::max(renderPpm,drift.ppm()):0)*1e-6);
        double last=phase+(outputFrames-1)*step;
        size_t required=size_t(std::ceil(last))+1;
        return required>count?std::min(required-count,capacity-count):0;
    }
    size_t freeFrames()const{return capacity-count;}
    size_t bufferedFrames()const{return count;}
    double correctionPpm()const{return adaptive?renderPpm:0;}
    int driftState()const{return adaptive?(primed?2:1):0;}
    double targetDelayMs()const{return adaptive?warmupFrames()*1000.0/outputRate:0;}
    uint64_t underflowFrames()const{return missing;}
    uint64_t overflowFrames()const{return dropped;}
    uint64_t starvationEvents()const{return starvations;}
    // Initial/recovery buffering silence is separate from tracking underflow.
    uint64_t primingSilenceFrames()const{return warmingSilence;}
    size_t push(const float* input,size_t frames,int channels){
        if(channels<1||channels>2){dropped+=frames;return 0;}
        size_t accepted=std::min(frames,capacity-count);dropped+=frames-accepted;
        for(size_t i=0;i<accepted;++i)samples[(head+count+i)%capacity]=channels==2?.5f*(input[i*2]+input[i*2+1]):input[i];
        count+=accepted;return accepted;
    }
    void render(float* output,int frames){
        if(frames<=0)return;
        int begin=0;
        if(adaptive&&!primed){
            double available=double(count)-double(warmupFrames())*ratio;
            int playable=available>0?int(std::min(double(frames),std::floor(available/ratio+1e-9))):0;
            begin=frames-playable;std::fill(output,output+begin,0.f);warmingSilence+=begin;
            if(begin==frames)return;
            primed=true;
        }
        int i=begin;double wanted=adaptive?drift.ppm():0;
        double ppmStep=InputDrift::slewPpmPerSecond/outputRate;
        for(;i<frames;++i){
            size_t a=size_t(phase);double fraction=phase-a;
            if(a>=count||(fraction>1e-9&&a+1>=count))break;
            float first=samples[(head+a)%capacity];
            output[i]=fraction<=1e-9?first:float(first+(samples[(head+a+1)%capacity]-first)*fraction);
            renderPpm+=std::clamp(wanted-renderPpm,-ppmStep,ppmStep);phase+=ratio*(1+renderPpm*1e-6);
        }
        if(i<frames){
            // Missing input is silence, never a stretched copy of a short read.
            std::fill(output+i,output+frames,0.f);missing+=frames-i;++starvations;rePrime();
        }else{
            size_t consumed=std::min(size_t(phase),count);head=(head+consumed)%capacity;count-=consumed;phase-=consumed;
            if(adaptive)drift.update(double(count)-phase,frames-begin,outputRate);
        }
    }
};
}
