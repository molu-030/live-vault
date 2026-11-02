#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>
namespace lm {
constexpr double PI=3.14159265358979323846;
inline float hz(int midi) { return 440.f*std::pow(2.f,(midi-69)/12.f); }
inline float limit(float x) { return std::clamp(x,-0.98f,0.98f); }
struct Note { int midi=60,velocity=90; double start=0,end=0; };
// Source-time spans retained in the WAV. Paused source time is omitted from MIDI too.
struct RecordedSpan { double sourceStart=0,duration=0; };
inline std::vector<Note> recordedNotes(const std::vector<Note>& notes,const std::vector<RecordedSpan>& spans) {
    std::vector<Note> out;double offset=0;
    for(const auto& span:spans){
        for(auto note:notes){double start=std::max(note.start,span.sourceStart),end=std::min(note.end,span.sourceStart+span.duration);
            if(end>start){note.start=offset+start-span.sourceStart;note.end=offset+end-span.sourceStart;out.push_back(note);}}
        offset+=span.duration;
    }
    return out;
}
struct Pitch { float frequency=0,confidence=0,rms=0; };
// YIN difference and cumulative normalization with parabolic interpolation.
inline Pitch detectPitch(const float* x,int n,int sr) {
    Pitch p; double energy=0; for(int i=0;i<n;i++) energy+=x[i]*x[i];
    p.rms=std::sqrt(energy/n); if(p.rms<0.0025f) return p;
    int maxLag=std::min(n/2-1,sr/75),minLag=std::max(2,sr/1100);
    std::array<float,1025> diff{};
    if(maxLag>1023)maxLag=1023;
    int window=n-maxLag; double sum=0;
    for(int lag=1;lag<=maxLag;lag++) {
        double d=0;for(int i=0;i<window;i++){float v=x[i]-x[i+lag];d+=v*v;}
        sum+=d; diff[lag]=sum>0?d*lag/sum:1;
    }
    int best=-1;
    for(int lag=minLag;lag<maxLag;lag++)if(diff[lag]<0.13f){
        while(lag+1<maxLag&&diff[lag+1]<diff[lag])lag++;
        best=lag;break;
    }
    if(best<0){
        int minIdx=minLag;
        for(int lag=minLag+1;lag<maxLag;lag++)if(diff[lag]<diff[minIdx])minIdx=lag;
        if(diff[minIdx]<0.30f)best=minIdx;
    }
    if(best<0)return p;
    float offset=0,den=2*(2*diff[best]-diff[best-1]-diff[best+1]);
    if(std::abs(den)>1e-8f)offset=(diff[best+1]-diff[best-1])/den;
    float frequency=sr/(best+offset);
    if(frequency>=75&&frequency<=1100){p.frequency=frequency;p.confidence=1-diff[best];}
    return p;
}
template <size_t Size> class Ring {
    std::vector<float> values=std::vector<float>(Size);
    std::atomic<uint64_t> readIndex{0},writeIndex{0};
public:
    size_t available()const{return writeIndex.load(std::memory_order_acquire)-readIndex.load(std::memory_order_acquire);}
    size_t push(const float* in,size_t count){
        uint64_t w=writeIndex.load(std::memory_order_relaxed),r=readIndex.load(std::memory_order_acquire);
        count=std::min(count,Size-size_t(w-r));
        for(size_t i=0;i<count;i++)values[(w+i)%Size]=in[i];
        writeIndex.store(w+count,std::memory_order_release);return count;
    }
    size_t pop(float* out,size_t count){
        uint64_t r=readIndex.load(std::memory_order_relaxed),w=writeIndex.load(std::memory_order_acquire);
        count=std::min(count,size_t(w-r));
        for(size_t i=0;i<count;i++)out[i]=values[(r+i)%Size];
        readIndex.store(r+count,std::memory_order_release);return count;
    }
    void reset(){readIndex.store(0);writeIndex.store(0);}
};
// Playback capture is mixed only into recorded audio, never into headphone monitoring.
class PlaybackMixer {
    Ring<262144> samples;
    std::atomic<bool> enabled{false};
    std::atomic<float> gain{.7f};
public:
    // Call only before the stream and capture producer start.
    void reset(){enabled=false;samples.reset();}
    void configure(bool on,float amount){gain=std::clamp(amount,0.f,1.5f);enabled=on;}
    size_t push(const float* data,size_t count){return enabled?samples.push(data,count-count%2):0;}
    void read(float* out,size_t count){
        size_t n=samples.pop(out,count);std::fill(out+n,out+count,0.f);
        if(!enabled)std::fill(out,out+count,0.f);
        else {float level=gain;for(size_t i=0;i<n;i++)out[i]*=level;}
    }
};
struct Biquad {
    float a1=0,a2=0,b0=1,b1=0,b2=0,z1=0,z2=0;
    void set(int type,float db,float f,float sr){
        double A=std::pow(10.,db/40.),w=2*PI*f/sr,c=std::cos(w),s=std::sin(w);
        double alpha=s/(2*0.8),beta=2*std::sqrt(A)*alpha,a0;
        double B0,B1,B2,A1,A2;
        if(type==0){B0=A*((A+1)-(A-1)*c+beta);B1=2*A*((A-1)-(A+1)*c);B2=A*((A+1)-(A-1)*c-beta);a0=(A+1)+(A-1)*c+beta;A1=-2*((A-1)+(A+1)*c);A2=(A+1)+(A-1)*c-beta;}
        else if(type==2){B0=A*((A+1)+(A-1)*c+beta);B1=-2*A*((A-1)+(A+1)*c);B2=A*((A+1)+(A-1)*c-beta);a0=(A+1)-(A-1)*c+beta;A1=2*((A-1)-(A+1)*c);A2=(A+1)-(A-1)*c-beta;}
        else {B0=1+alpha*A;B1=-2*c;B2=1-alpha*A;a0=1+alpha/A;A1=-2*c;A2=1-alpha/A;}
        b0=B0/a0;b1=B1/a0;b2=B2/a0;a1=A1/a0;a2=A2/a0;
    }
    float tick(float x){float y=b0*x+z1;z1=b1*x-a1*y+z2+1e-25f;z2=b2*x-a2*y;return y;}
};
class Reverb {
    static constexpr int NUM_COMBS = 8;
    static constexpr int NUM_ALLPASS = 4;
    std::array<std::vector<float>, NUM_COMBS> combLines;
    std::array<size_t, NUM_COMBS> combPos{};
    std::array<float, NUM_COMBS> combFilter{}, combFeedback{};
    std::array<std::vector<float>, NUM_ALLPASS> allpassLines;
    std::array<size_t, NUM_ALLPASS> allpassPos{};
    float cachedDecay = -1, roomFeedback = 0.7f, damping = 0.25f;
    int cachedType = -1;
    float rate = 48000;
public:
    void init(int sr, int stereo) {
        rate = float(sr);
        cachedDecay = -1;
        cachedType = -1;
        const int combTunings[NUM_COMBS] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        const int allpassTunings[NUM_ALLPASS] = {556, 441, 341, 225};
        float scale = rate / 44100.f;
        int spread = stereo * 23;
        for (int i = 0; i < NUM_COMBS; i++) {
            int len = std::max(16, int(combTunings[i] * scale) + spread);
            combLines[i].assign(len, 0.f);
            combPos[i] = 0;
            combFilter[i] = 0.f;
        }
        for (int i = 0; i < NUM_ALLPASS; i++) {
            int len = std::max(8, int(allpassTunings[i] * scale) + spread);
            allpassLines[i].assign(len, 0.f);
            allpassPos[i] = 0;
        }
    }

    float tick(float x, float decay, int type) {
        if (decay != cachedDecay || type != cachedType) {
            cachedDecay = decay;
            cachedType = type;
            damping = type == 2 ? 0.12f : type == 3 ? 0.45f : 0.25f;
            float targetSec = std::clamp(decay, 0.3f, 6.0f);
            for (int i = 0; i < NUM_COMBS; i++) combFeedback[i] = std::pow(0.001f, float(combLines[i].size()) / (rate * targetSec));
        }

        // 8 Parallel Lowpass Feedback Comb Filters
        float sum = 0.f;
        for (int i = 0; i < NUM_COMBS; i++) {
            auto& line = combLines[i];
            size_t& pos = combPos[i];
            float out = line[pos];
            combFilter[i] = out * (1.f - damping) + combFilter[i] * damping + 1e-25f;
            line[pos] = x * 0.125f + combFilter[i] * combFeedback[i];
            if (++pos >= line.size()) pos = 0;
            sum += out;
        }

        // 4 Series Allpass Filters
        float ap = sum;
        for (int i = 0; i < NUM_ALLPASS; i++) {
            auto& line = allpassLines[i];
            size_t& pos = allpassPos[i];
            float buf = line[pos];
            line[pos] = ap + buf * 0.5f + 1e-25f;
            ap = buf - ap * 0.5f;
            if (++pos >= line.size()) pos = 0;
        }
        return ap;
    }
};

class DSP {
    Biquad low, mid, high;
    Reverb left, right;
    std::vector<float> delay;
    std::vector<float> preDelay;
    size_t di = 0, prePos = 0;
    float currentOffset = 1000.f, compTarget = 1.f, compGain = 1.f;
    int compCounter = 0;
    float hpX = 0, hpY = 0, envelope = 0, delayToneL = 0, delayToneR = 0;
    int sr = 48000;
    float cachedLow = 999, cachedMid = 999, cachedHigh = 999;
public:
    void init(int rate) {
        sr = rate;
        left.init(sr, 0);
        right.init(sr, 1);
        preDelay.assign(std::max(1, int(sr * 0.015f)), 0.f);
        prePos = 0;
        delay.assign(sr * 2, 0.f);
        di = 0;
        currentOffset = float(sr / 4);
        hpX = hpY = envelope = delayToneL = delayToneR = 0.f;
        cachedLow = cachedMid = cachedHigh = 999.f;
        compTarget = compGain = 1.f; compCounter = 0;
    }

    void configure(const float* p) {
        if (p[7] != cachedLow) { low.set(0, p[7], 180, sr); cachedLow = p[7]; }
        if (p[8] != cachedMid) { mid.set(1, p[8], 1400, sr); cachedMid = p[8]; }
        if (p[9] != cachedHigh) { high.set(2, p[9], 6500, sr); cachedHigh = p[9]; }
    }

    void tick(float input, const float* p, float& l, float& r) {
        float x = input - hpX + 0.990f * hpY;
        hpX = input;
        hpY = x;

        x = high.tick(mid.tick(low.tick(x * p[0])));

        float level = std::abs(x);
        envelope += (level - envelope) * (level > envelope ? 0.008f : 0.00012f) + 1e-25f;

        // Compressor: exact pow() evaluated every 16 samples, gain smoothed per sample
        if ((compCounter++ & 15) == 0)
            compTarget = (p[10] > 0.5f && envelope > 0.16f) ? std::pow(0.16f / envelope, 0.65f) : 1.f;
        compGain += (compTarget - compGain) * 0.02f;
        x *= compGain;

        if (p[19] > 0.5f && envelope < 0.008f) {
            x *= std::clamp(envelope / 0.008f, 0.f, 1.f);
        }

        // Smooth delay time tracking & linear interpolation with stereo Haas widening
        float targetOffset = std::clamp(p[5] * sr / 1000.f, 1.f, float(delay.size() - 2));
        currentOffset += (targetOffset - currentOffset) * 0.005f;

        auto readDelay = [&](float off) {
            float readPos = float(di) + float(delay.size()) - off;
            size_t i0 = size_t(readPos) % delay.size();
            size_t i1 = (i0 + 1) % delay.size();
            float frac = readPos - size_t(readPos);
            return delay[i0] * (1.f - frac) + delay[i1] * frac;
        };

        float echoL = readDelay(currentOffset);
        float offsetR = std::min(float(delay.size() - 2), currentOffset + sr * 0.011f);
        float echoR = readDelay(offsetR);

        delayToneL += (echoL - delayToneL) * 0.40f + 1e-25f;
        delayToneR += (echoR - delayToneR) * 0.40f + 1e-25f;
        float feedback = (delayToneL + delayToneR) * 0.5f * std::clamp(p[6], 0.f, 0.70f);
        delay[di] = x + feedback;
        di = (di + 1) % delay.size();

        // Vocal reverb pre-delay (~15ms)
        float preOut = preDelay[prePos];
        preDelay[prePos] = x;
        prePos = (prePos + 1) % preDelay.size();

        float revL = left.tick(preOut, p[3], int(p[18]));
        float revR = right.tick(preOut, p[3], int(p[18]));

        float dryMix = 1.f - p[2] * 0.25f;
        l = x * dryMix + revL * p[2] + delayToneL * p[4];
        r = x * dryMix + revR * p[2] + delayToneR * p[4];
    }
};
inline std::vector<uint8_t> midiFile(const std::vector<Note>& notes,int bpm){
    bpm=std::clamp(bpm,40,240);constexpr int ppq=480;
    struct Event {uint32_t tick;int type,note,velocity;};std::vector<Event> events;
    for(const auto& n:notes){if(n.end<=n.start)continue;
        uint32_t a=std::llround(std::max(0.,n.start)*bpm*ppq/60.);
        uint32_t b=std::max(a+1,uint32_t(std::llround(std::max(0.,n.end)*bpm*ppq/60.)));
        events.push_back({a,0x90,std::clamp(n.midi,0,127),std::clamp(n.velocity,1,127)});events.push_back({b,0x80,std::clamp(n.midi,0,127),0});}
    std::sort(events.begin(),events.end(),[](const Event&a,const Event&b){return a.tick==b.tick?a.type<b.type:a.tick<b.tick;});
    int tempo=60000000/bpm;std::vector<uint8_t> track={0,0xff,0x51,3,uint8_t(tempo>>16),uint8_t(tempo>>8),uint8_t(tempo),0,0xc0,53};
    auto vlq=[&](uint32_t v){uint8_t b[5];int n=0;b[n++]=v&127;while((v>>=7))b[n++]=(v&127)|128;while(n)track.push_back(b[--n]);};
    uint32_t previous=0;for(auto e:events){vlq(e.tick-previous);previous=e.tick;track.push_back(e.type);track.push_back(e.note);track.push_back(e.velocity);}
    track.insert(track.end(),{0,0xff,0x2f,0});
    std::vector<uint8_t> result={'M','T','h','d',0,0,0,6,0,0,0,1,1,0xe0,'M','T','r','k'};uint32_t len=track.size();
    for(int s=24;s>=0;s-=8) result.push_back(len>>s);
    result.insert(result.end(),track.begin(),track.end());
    return result;
}
}
