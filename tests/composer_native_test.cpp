#include <cassert>
#include <cstdlib>
#include <iostream>
#include <memory>
static bool rendering=false;static int allocations=0;
static void* trackedMalloc(size_t n){if(rendering)allocations++;return std::malloc(n);}
static void* trackedRealloc(void* p,size_t n){if(rendering)allocations++;return std::realloc(p,n);}
#define TSF_FREE std::free
#define TSF_MALLOC trackedMalloc
#define TSF_REALLOC trackedRealloc
#include "../app/src/main/cpp/composer_engine.h"

static void coarseAddressOffsets(){
    for(int value:{-32768,-1,0,1,32767}){
        tsf_hydra_genamount amount{};amount.shortAmount=static_cast<short>(value);
        for(int generator:{4,12,45,50}){
            tsf_region region{};
            region.offset=region.end=region.loop_start=region.loop_end=0x80000000u;
            tsf_region_operator(&region,static_cast<tsf_u16>(generator),&amount,nullptr);
            // Unsigned multiplication defines the original modulo-address behavior.
            unsigned expected=0x80000000u+static_cast<unsigned>(value)*32768u;
            assert(region.offset==(generator==4?expected:0x80000000u));
            assert(region.end==(generator==12?expected:0x80000000u));
            assert(region.loop_start==(generator==45?expected:0x80000000u));
            assert(region.loop_end==(generator==50?expected:0x80000000u));
        }
    }
    std::cout<<"PASS: coarse sample address offsets preserve signed-short boundaries and unsigned accumulation\n";
}
static void tracker(){
    for(int sr:{44100,48000}){
        lv::VoiceTracker tracker;std::array<float,2048> window{};int onset=-1,off=-1;
        int hop=sr/100;
        for(int frame=0;frame<30;frame++){
            std::move(window.begin()+hop,window.end(),window.begin());
            for(int i=0;i<hop;i++){int n=frame*hop+i;window[2048-hop+i]=.1f*std::sin(2*lm::PI*196*n/sr);}
            if((frame+1)*hop<2048)continue;
            auto pitch=lm::detectPitch(window.data(),2048,sr);auto command=tracker.process(pitch,0,.85,.007);
            if(command.kind==1&&onset<0)onset=(frame+1)*10;
        }
        assert(onset>0&&onset<=100);
        for(int i=0;i<3;i++){auto c=tracker.process({},0,.85,.007);if(c.kind==0)off=i;}
        assert(off==1);
    }
    lv::VoiceTracker tracker;lm::Pitch tone{261.63,.99,.08};
    assert(tracker.process(tone,0,.85,.007).kind==-1);assert(tracker.process(tone,0,.85,.007).kind==1);
    for(int i=0;i<30;i++){tone.frequency=261.63f*std::pow(2.f,.55f*std::sin(i*.4f)/12);assert(tracker.process(tone,0,.85,.007).kind==2);}
    tone.frequency=261.63;tone.rms=.02;for(int i=0;i<10;i++)tracker.process(tone,0,.85,.007);tone.rms=.1;assert(tracker.process(tone,0,.85,.007).kind==1);
    assert(tracker.process({440,.5,.1},0,.85,.007).kind==-1);
    std::cout<<"PASS: 44.1/48kHz <100ms synthetic onset, repeated articulation, vibrato hysteresis, confidence and silence\n";
}
static void synth(const char* path,int sr){
    auto engine=std::make_unique<lv::ComposerEngine>();assert(engine->load(path));engine->sampleRate(sr);assert(!engine->kits().empty());
    std::vector<lv::Event> events;events.push_back({0,3,0,0,1,0,0,0,0});
    for(int ch=0;ch<8;ch++){events.push_back({0,2,ch,0,0,ch==2?0:24+ch,ch==2?128:0,0,.7});events.push_back({0,1,ch,ch==2?36:48+ch,.8,0,0,0,0});events.push_back({.5,0,ch,ch==2?36:48+ch,0,0,0,0,0});}
    std::stable_sort(events.begin(),events.end(),[](auto a,auto b){return a.beat<b.beat;});
    assert(engine->publish(events,4,4,240,0,0));engine->settings(true,33,0,0,true,false,0,-1,false,false,.85,.007);
    engine->transport(3);engine->record(true);lv::VoiceCommand on;on.kind=1;on.input=on.midi=60;engine->voiceCommands.push(on);
    std::array<float,512> music{},voice{};std::array<float,256> count{};double energy=0;
    for(int b=0;b<sr/256*3;b++){rendering=true;engine->render(music.data(),voice.data(),count.data(),256);rendering=false;for(float f:music){assert(std::isfinite(f));energy+=f*f;}}
    assert(energy>0);assert(engine->outputNote==36);assert(engine->recording==3);assert(allocations==0);
    lv::Performance p;bool hadOn=false,hadOff=false;while(engine->performance.pop(p)){hadOn|=p.kind==1;hadOff|=p.kind==0;assert(p.beat>=0);}assert(hadOn&&hadOff);
    engine->transport(0);engine->settings(false,33,0,0,true,false,0,-1,false,false,.85,.007);
    for(int b=0;b<sr/256*2;b++){engine->render(music.data(),voice.data(),count.data(),256);}
    double tail=0;for(float f:music)tail+=std::abs(f);assert(tail<.0001);for(float f:voice)assert(f==0);
    std::cout<<"PASS: "<<sr<<"Hz eight-track + voice, fixed allocation, count-in, phrase closure, stop silence\n";
}
static void clearSoloPreservesMix(const char* path){
    auto reference=std::make_unique<lv::ComposerEngine>();auto actual=std::make_unique<lv::ComposerEngine>();
    assert(reference->load(path));assert(actual->load(path));
    std::vector<lv::Event> events={{0,3,0,0,1,0,0,0,0},{0,2,0,0,0,24,0,0,.7},{0,2,1,0,0,24,0,0,.7},{0,1,0,60,.8,0,0,0,0},{0,1,1,64,.8,0,0,0,0}};
    for(auto* e:{reference.get(),actual.get()}){e->sampleRate(44100);assert(e->publish(events,4,4,120,0,0));e->transport(1);}
    std::array<float,512> left{},right{},v1{},v2{};std::array<float,256> c1{},c2{};double energy=0;
    reference->render(left.data(),v1.data(),c1.data(),256);actual->render(right.data(),v2.data(),c2.data(),256);
    for(auto* e:{reference.get(),actual.get()}){e->track(0,.25f,-.8f,false,false);e->track(1,.8f,.6f,true,false);}
    actual->track(0,.25f,-.8f,false,true);actual->clearSolo();
    for(int block=0;block<32;block++){reference->render(left.data(),v1.data(),c1.data(),256);actual->render(right.data(),v2.data(),c2.data(),256);for(size_t i=0;i<left.size();i++){assert(std::abs(left[i]-right[i])<1e-7);energy+=left[i]*left[i];}}
    assert(energy>0);std::cout<<"PASS: clearing solo preserves volume, pan and mute\n";
}
static void muteEndsAtPartBoundary(const char* path,int sr){
    auto e=std::make_unique<lv::ComposerEngine>();assert(e->load(path));e->sampleRate(sr);
    std::vector<lv::Event> events={{0,3,0,0,1,0,0,0,0},{0,2,0,0,0,24,0,0,.8},{0,1,0,60,.8,0,0,0,0},{.5,0,0,60,0,0,0,0,0},{1,3,0,1,1,0,0,0,0},{1,2,0,0,0,24,0,0,.8},{1,1,0,64,.8,0,0,0,0}};
    assert(e->publish(events,4,1,120,0,0));e->transport(1);
    std::array<float,512> music{},voice{};std::array<float,256> count{};
    e->render(music.data(),voice.data(),count.data(),256);e->track(0,.8f,0,true,false);
    double muted=0,restored=0;int blocks=sr/256;
    for(int b=0;b<blocks;b++){e->render(music.data(),voice.data(),count.data(),256);for(float f:music){if(b>blocks/4&&b<blocks*2/5)muted+=f*f;if(b>blocks*3/4)restored+=f*f;}}
    assert(muted<1e-8);assert(restored>1e-5);
    std::cout<<"PASS: "<<sr<<"Hz immediate mute ends at next part\n";
}
static void liveRhythmBounds(const char* path){
    std::vector<float> reference(200*512);
    for(int pattern:{0,8,-1,9,999}){
        auto e=std::make_unique<lv::ComposerEngine>();assert(e->load(path));e->sampleRate(48000);
        std::vector<lv::Event> events={{0,3,0,0,1,pattern,0,0,0}};
        assert(e->publish(events,4,4,240,0,0));
        e->settings(true,24,1,0,false,false,0,0,true,false,.85,.007);
        e->transport(1);
        std::array<float,512> music{},voice{};std::array<float,256> count{};
        e->render(music.data(),voice.data(),count.data(),256);
        lv::VoiceCommand on;on.kind=1;on.input=on.midi=60;on.velocity=100;on.expression=1;
        e->voiceCommands.push(on);
        double energy=0;
        for(int b=0;b<200;b++){rendering=true;e->render(music.data(),voice.data(),count.data(),256);rendering=false;for(size_t i=0;i<voice.size();i++){float f=voice[i];assert(std::isfinite(f));energy+=f*f;if(pattern==0)reference[b*512+i]=f;else if(pattern!=8)assert(f==reference[b*512+i]);}}
        assert(energy>0);assert(allocations==0);
    }
    std::cout<<"PASS: live chord hip-hop rhythm index8 and invalid-index fallback, fixed allocation\n";
}
int main(int argc,char**argv){assert(argc==2);coarseAddressOffsets();tracker();liveRhythmBounds(argv[1]);clearSoloPreservesMix(argv[1]);muteEndsAtPartBoundary(argv[1],44100);muteEndsAtPartBoundary(argv[1],48000);synth(argv[1],44100);synth(argv[1],48000);}
