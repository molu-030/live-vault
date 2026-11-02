#include "../app/src/main/cpp/feedback_guard.h"
#include <cstdlib>
#include <iostream>
#include <limits>
#include <random>
using namespace lm;
namespace {
constexpr int N=2048;
void require(bool condition,const char* message){if(!condition){std::cerr<<"FAIL: "<<message<<'\n';std::exit(1);}}
struct Fixture {
    FeedbackGuard guard;float wave[N]{};int sr;double time=0;
    explicit Fixture(int rate):sr(rate){guard.init(sr,true);}
    void analyze(){guard.analyze(wave,N,sr,time);time+=.1;}
    void tone(float frequency,float amplitude){for(int i=0;i<N;i++)wave[i]=amplitude*std::sin(2*PI*frequency*i/sr);analyze();}
    void growth(float hz){for(float a:{.03f,.05f,.08f,.14f})tone(hz,a);}
    void quiet(){std::fill(wave,wave+N,0);analyze();}
};
void repeatedGrowth(int sr,float hz){
    Fixture f(sr);
    for(int cycle=1;cycle<=3;cycle++){
        f.growth(hz);
        require(f.guard.count()==1,"same tone must retain one notch");
        require(f.guard.muted()==(cycle==3),"three fresh rises at one frequency must escalate to mute");
        if(cycle==1){
            f.guard.prepare(sr);double filtered=0,original=0;
            for(int i=0;i<sr/2;i++){
                float x=.3f*std::sin(2*PI*hz*i/sr),left=x,right=x;
                f.guard.process(left,right);
                require(std::isfinite(left)&&std::isfinite(right),"notch processing must remain finite");
                if(i>sr/4){filtered+=left*left+right*right;original+=2*x*x;}
            }
            if(!(filtered<original*.4))std::cerr<<"attenuation sr="<<sr<<" hz="<<hz<<" energy ratio="<<filtered/original<<'\n';
            require(filtered<original*.4,"detected feedback notch must attenuate the actual tone energy");
        }

    }
    f.guard.prepare(sr);float left=.21f,right=-.32f;f.guard.process(left,right);
    require(left==0&&right==0,"latched protection must mute both stereo channels");
    f.guard.reset();f.quiet();f.guard.prepare(sr);
    require(!f.guard.muted()&&f.guard.count()==0,"explicit reset clears trip and detector history");
}
void benignSignals(int sr){
    Fixture f(sr);std::mt19937 rng(773);std::uniform_real_distribution<float> random(-1,1);
    for(int frame=0;frame<50;frame++)f.tone(1500,.01f); // Concentrated signal below detection gate.
    require(!f.guard.muted()&&f.guard.count()==0,"quiet tones must not trip");
    f.quiet();
    for(int frame=0;frame<50;frame++)f.tone(1500,.15f); // Consecutive sustained tone without growth.
    require(!f.guard.muted()&&f.guard.count()==0,"consecutive stable tone windows must not trip");
    f.quiet();
    for(int frame=0;frame<60;frame++){
        float amplitude=.06f+.18f*(frame%6)/5.f;
        for(int i=0;i<N;i++){
            double t=(frame*N+i)/double(sr),phase=2*PI*(220*t+.006*std::sin(2*PI*5*t));
            f.wave[i]=amplitude*(.55f*std::sin(phase)+.3f*std::sin(2*phase)+.2f*std::sin(3*phase)+.15f*std::sin(4*phase));
        }
        f.analyze();
    }
    require(!f.guard.muted()&&f.guard.count()==0,"voiced harmonic vibrato with changing loudness must not trip");
    for(int frame=0;frame<40;frame++){
        for(float& x:f.wave)x=random(rng)*(.05f+.15f*(frame%5)/4.f);
        f.analyze();
    }
    require(!f.guard.muted()&&f.guard.count()==0,"breath and broadband noise must not trip");
    for(int frame=0;frame<5;frame++){f.quiet();f.tone(1500,1.f);f.quiet();}
    require(!f.guard.muted()&&f.guard.count()==0,"isolated transient must not count as growing feedback");
}
void outOfBandTones(int sr){
    for(float hz:{75.f,100.f,120.f,9000.f,12000.f}){
        Fixture f(sr);
        for(int cycle=0;cycle<3;cycle++){
            f.growth(hz);
            require(f.guard.count()==0&&!f.guard.muted(),"out-of-band growth must not become an in-band feedback incident");
        }
    }
}
void overloadAndRoutes(int sr){
    Fixture f(sr);f.tone(1500,1);require(!f.guard.muted(),"one overload window must not latch");
    f.tone(1500,1);require(f.guard.muted(),"two consecutive overload windows must latch");
    f.guard.setEnabled(false);require(!f.guard.isEnabled()&&!f.guard.muted(),"safe output disables active protection");
    float left=.83f,right=-.79f;f.guard.process(left,right);
    require(left==.83f&&right==-.79f,"disabled guard must pass both channels unchanged");
    f.guard.setEnabled(true);require(f.guard.isEnabled()&&f.guard.muted(),"route transition must preserve safety latch");
    f.guard.init(sr,true,true);require(f.guard.muted(),"stream reopen with preserve flag must retain safety latch");
    f.guard.init(sr,false,true);require(!f.guard.muted(),"headphone stream reopen bypasses active mute");
    f.guard.init(sr,true,true);require(f.guard.muted(),"returning to risky stream must restore retained latch");
    f.guard.reset();f.quiet();require(!f.guard.muted(),"explicit reset must recover after route transition");
    f.growth(1500);require(f.guard.count()==1,"growth still works after reset");
    f.guard.setEnabled(false);f.guard.setEnabled(true);f.quiet();
    require(f.guard.count()==0,"route transition clears stale detector history");
    left=.9f;right=-.9f;f.guard.process(left,right);
    require(std::abs(left)<=.45f&&std::abs(right)<=.45f,"enabled guard caps live monitor amplitude");
}
void nonfiniteMonitorOutput(){
    for(float bad:{std::numeric_limits<float>::quiet_NaN(),std::numeric_limits<float>::infinity(),-std::numeric_limits<float>::infinity()}){
        for(bool badLeft:{false,true}){
            Fixture f(48000);float left=badLeft?bad:.2f,right=badLeft?-.3f:bad;
            f.guard.process(left,right);
            require(left==0&&right==0&&f.guard.muted(),"nonfinite monitor channel must zero stereo and latch protection");
            f.guard.reset();f.quiet();f.guard.prepare(48000);left=.2f;right=-.3f;f.guard.process(left,right);
            require(!f.guard.muted()&&std::isfinite(left)&&std::isfinite(right)&&left==.2f&&right==-.3f,"explicit reset must recover finite monitor processing");
        }
    }
}
void invalidInput(){
    Fixture f(48000);float single=.8f;
    for(int size:{std::numeric_limits<int>::min(),-1,0,1,2047,2049,std::numeric_limits<int>::max()})f.guard.analyze(&single,size,48000,0);
    f.guard.analyze(nullptr,N,48000,0);
    for(int sr:{0,-1,8000,44100,std::numeric_limits<int>::max()})f.guard.analyze(f.wave,N,sr,0);
    for(double time:{std::numeric_limits<double>::quiet_NaN(),std::numeric_limits<double>::infinity(),-std::numeric_limits<double>::infinity()})f.guard.analyze(f.wave,N,48000,time);
    for(float bad:{std::numeric_limits<float>::quiet_NaN(),std::numeric_limits<float>::infinity(),-std::numeric_limits<float>::infinity()}){
        for(int index:{0,N/2,N-1}){f.wave[index]=bad;f.analyze();f.wave[index]=0;}
    }
    require(!f.guard.muted()&&f.guard.count()==0,"invalid input must not create a notch or trip");
    f.tone(1500,1);f.guard.analyze(nullptr,N,48000,0);f.tone(1500,1);
    require(!f.guard.muted(),"invalid block interrupts consecutive overload detection");
    f.quiet();f.growth(1500);require(f.guard.count()==1,"valid analysis recovers after invalid input");
    for(int sr:{-1,0,7999,384001,std::numeric_limits<int>::max()}){FeedbackGuard g;g.init(sr,true);g.analyze(&single,N,sr,0);require(!g.isEnabled()&&!g.muted(),"unsupported init sample rate disables detector safely");}
}
}
int main(){
    for(int sr:{44100,48000}){
        for(float hz:{250.f,400.f,1500.f,6000.f})repeatedGrowth(sr,hz);
        benignSignals(sr);outOfBandTones(sr);overloadAndRoutes(sr);
    }
    invalidInput();nonfiniteMonitorOutput();
    std::cout<<"PASS: feedback frequency coverage, repeated incidents, benign audio, stereo latch/reset, routes and invalid inputs\n";
}
