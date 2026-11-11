#include "../app/src/main/cpp/audio_core.h"
#include "../app/src/main/cpp/feedback_guard.h"
#include "../app/src/main/cpp/local_seek.h"
#include <cassert>
#include <iostream>
#include <limits>
using namespace lm;
int main(){
    LocalSeek seek;assert(seek.peek()<0);assert(!seek.request(1,0));assert(!seek.request(std::numeric_limits<double>::quiet_NaN(),10));
    assert(seek.request(-2,10)&&seek.peek()==0);assert(seek.request(50,10)&&seek.peek()==10);assert(seek.request(3.5,10)&&seek.consume()==3.5);assert(seek.consume()<0);assert(seek.request(2,10));seek.clear();assert(seek.peek()<0);
    std::cout<<"PASS: local seek bounds, invalid input, latest request and clearing\n";
    for(int sr:{44100,48000})for(float frequency:{82.4069f,130.8128f,220.f,440.f,880.f,1046.502f}){
        float data[2048];for(int i=0;i<2048;i++)data[i]=.3f*std::sin(2*PI*frequency*i/sr)+.07f*std::sin(4*PI*frequency*i/sr);
        auto p=detectPitch(data,2048,sr);double cents=1200*std::log2(p.frequency/frequency);assert(p.frequency>0&&std::abs(cents)<8);}
    float silence[2048]={};assert(detectPitch(silence,2048,48000).frequency==0);
    // Quiet voice pitch detection (rms ~ 0.004f, below old 0.007f gate, above new 0.0025f gate)
    {
        float quietVoice[2048];
        for(int i=0;i<2048;i++) quietVoice[i]=.00566f*std::sin(2*PI*220.f*i/48000);
        auto qp=detectPitch(quietVoice,2048,48000);
        assert(qp.rms>=0.0035f&&qp.rms<=0.0045f);
        assert(qp.frequency>0&&std::abs(1200*std::log2(qp.frequency/220.f))<8);
        assert(qp.confidence>0.8f);
    }
    auto midi=midiFile({{60,100,0,.5},{64,80,.5,1}},120);assert(std::string(midi.begin(),midi.begin()+4)=="MThd");assert(midi[12]==1&&midi[13]==224);
    uint32_t len=(uint32_t(midi[18])<<24)|(uint32_t(midi[19])<<16)|(uint32_t(midi[20])<<8)|midi[21];assert(len+22==midi.size());
    assert(midi[midi.size()-3]==255&&midi[midi.size()-2]==47&&midi.back()==0);
    Ring<8> ring;float input[]={1,2,3,4,5,6},out[8];assert(ring.push(input,6)==6);assert(ring.pop(out,4)==4);assert(out[0]==1&&out[3]==4);assert(ring.push(input,6)==6);assert(ring.push(input,1)==0);assert(ring.pop(out,8)==8);assert(out[0]==5&&out[1]==6&&out[7]==6);
    auto retained=recordedNotes({{60,90,9,12},{64,90,12.5,13.5},{67,90,14,17}},{{10,2},{14,2}});
    assert(retained.size()==2);assert(retained[0].midi==60&&retained[0].start==0&&retained[0].end==2);
    assert(retained[1].midi==67&&retained[1].start==2&&retained[1].end==4);
    auto crossing=recordedNotes({{60,90,0,10}},{{1,2},{5,1},{8,0}});
    assert(crossing.size()==2&&crossing[1].start==2&&crossing[1].end==3);
    assert(recordedNotes({{60,90,0,1}},{}).empty());
    DSP dsp;dsp.init(48000);float params[]={1,.55,.4,2.2,.2,280,.25,0,0,1,1,0,0,0,0,78,0,0,0,1};dsp.configure(params);double tail=0;
    for(int i=0;i<48000*3;i++){float l,r;dsp.tick(i==0?.7f:0,params,l,r);assert(std::isfinite(l)&&std::isfinite(r));assert(std::abs(l)<2&&std::abs(r)<2);if(i>4800&&i<24000)tail+=l*l+r*r;}
    assert(tail>.00001);std::cout<<"PASS: pitch at 44.1/48 kHz, silence gate, SMF structure, SPSC wrapping, stable DSP/reverb tail\n";
    // Stereo Haas echo verification: left and right channels differ during echo repeats when p[4] > 0
    {
        DSP haasDsp; haasDsp.init(48000);
        float echoParams[]={1,0,0,2.2,.4f,120,.4f,0,0,0,0,0,0,0,0,78,0,0,0,0};
        haasDsp.configure(echoParams);
        double diffStereo=0;
        for(int i=0;i<48000;i++){
            float l,r;
            haasDsp.tick(i==0?0.8f:0.f,echoParams,l,r);
            assert(std::isfinite(l)&&std::isfinite(r));
            if(i>5500&&i<15000)diffStereo+=std::abs(l-r);
        }
        assert(diffStereo>0.01);
    }
    // Smooth delay modulation stability
    {
        DSP modDsp; modDsp.init(48000);
        float modParams[]={1,0,0,2.2,.4f,100,.3f,0,0,0,0,0,0,0,0,78,0,0,0,0};
        modDsp.configure(modParams);
        for(int i=0;i<48000*2;i++){
            float l,r;
            modParams[5]=100.f+400.f*float(i)/(48000.f*2.f);
            modDsp.tick(std::sin(2*PI*300*i/48000)*0.1f,modParams,l,r);
            assert(std::isfinite(l)&&std::isfinite(r));
            assert(std::abs(l)<2&&std::abs(r)<2);
        }
    }
    std::cout<<"PASS: stereo Haas echo and delay modulation stability\n";
    // Compare identical DSP paths with only the noise-reduction switch changed.
    float cleanParams[]={1,0,0,2.2,0,280,0,0,0,0,0,0,0,0,0,78,0,0,0,0};
    float reducedParams[20];std::copy(cleanParams,cleanParams+20,reducedParams);reducedParams[19]=1;
    DSP cleanNoise,reducedNoise,cleanVoice,reducedVoice;
    for(DSP* path:{&cleanNoise,&reducedNoise,&cleanVoice,&reducedVoice})path->init(48000);
    cleanNoise.configure(cleanParams);reducedNoise.configure(reducedParams);cleanVoice.configure(cleanParams);reducedVoice.configure(reducedParams);
    double cleanEnergy=0,reducedEnergy=0,cleanVoiceEnergy=0,reducedVoiceEnergy=0;
    for(int i=0;i<48000;i++){
        float noise=.002f*std::sin(2*PI*900*i/48000),voice=.08f*std::sin(2*PI*220*i/48000),a,b,c,d,e,f,g,h;
        cleanNoise.tick(noise,cleanParams,a,b);reducedNoise.tick(noise,reducedParams,c,d);
        cleanVoice.tick(voice,cleanParams,e,f);reducedVoice.tick(voice,reducedParams,g,h);
        if(i>12000){cleanEnergy+=a*a;reducedEnergy+=c*c;cleanVoiceEnergy+=e*e;reducedVoiceEnergy+=g*g;}
    }
    assert(cleanEnergy>.001);assert(reducedEnergy<cleanEnergy*.1);assert(reducedVoiceEnergy>cleanVoiceEnergy*.95);
    std::cout<<"PASS: noise reduction suppresses quiet signal while preserving voiced signal\n";
    FeedbackGuard guard;guard.init(48000,true);float wave[2048];
    for(int k=0;k<8;k++)guard.analyze(silence,2048,48000,k*.1);
    assert(!guard.muted()&&guard.count()==0);
    float amplitudes[]={.05f,.075f,.12f,.19f,.3f};
    for(int k=0;k<5;k++){for(int i=0;i<2048;i++)wave[i]=amplitudes[k]*std::sin(2*PI*1500*i/48000);guard.analyze(wave,2048,48000,1+k*.1);}
    assert(guard.count()>=1&&!guard.muted());guard.prepare(48000);double filtered=0,original=0;
    for(int i=0;i<24000;i++){float x=.3f*std::sin(2*PI*1500*i/48000),l=x,r=x;guard.process(l,r);assert(std::isfinite(l));if(i>12000){filtered+=l*l;original+=x*x;}}
    assert(filtered<original*.4);
    for(int i=0;i<2048;i++)wave[i]=std::sin(2*PI*1500*i/48000);
    guard.analyze(wave,2048,48000,5);guard.analyze(wave,2048,48000,5.1);assert(guard.muted());
    float l=.4f,r=.4f;guard.process(l,r);assert(l==0&&r==0);
    guard.reset();guard.analyze(silence,2048,48000,6);assert(!guard.muted()&&guard.count()==0);
    std::cout<<"PASS: silence stability, growing-tone suppression, overload mute and explicit reset\n";
    PlaybackMixer playback;float stereo[]={.4f,-.2f,.8f,-.6f},mixed[6];
    assert(playback.push(stereo,4)==0);
    playback.configure(true,.5f);assert(playback.push(stereo,4)==4);playback.read(mixed,6);
    assert(std::abs(mixed[0]-.2f)<1e-6&&std::abs(mixed[1]+.1f)<1e-6&&std::abs(mixed[2]-.4f)<1e-6);
    assert(mixed[4]==0&&mixed[5]==0);
    assert(playback.push(stereo,4)==4);playback.configure(false,1);playback.read(mixed,4);
    for(float v:mixed)assert(v==0);
    playback.configure(true,1);playback.read(mixed,6);for(float v:mixed)assert(v==0);
    assert(playback.push(stereo,4)==4);playback.reset();playback.configure(true,1);playback.read(mixed,6);for(float v:mixed)assert(v==0);
    std::cout<<"PASS: captured stereo gain, underflow silence, disabled capture drain, fresh-session isolation\n";
}
