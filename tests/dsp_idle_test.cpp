#include "../app/src/main/cpp/audio_core.h"
#include <cassert>
#include <iostream>
using namespace lm;
static void equivalent(int rate, bool longEcho) {
    DSP reference, actual; reference.init(rate); actual.init(rate);
    float p[]={1,.55f,.4f,6,.35f,longEcho?1800.f:120.f,longEcho?.25f:.3f,0,0,1,1,0,0,0,0,78,0,0,0,1};
    double lateTail=0;float maxError=0;
    for(int i=0;i<rate*50;i++) {
        // Empty playback first, then a voice burst, its entire tail, then resume.
        float input=(i>=rate&&i<rate+rate/10)||(i>=rate*48&&i<rate*48+rate/10)
                ?.15f*std::sin(2*PI*220*i/rate):0;
        if(i==rate/2){p[5]=longEcho?1900.f:160.f;p[2]=.65f;p[3]=6;p[9]=4;}
        reference.configure(p); actual.configure(p);
        float l,r,a,b;reference.tick(input,p,l,r);
        actual.process(input,p,a,b);
        maxError=std::max(maxError,std::max(std::abs(a-l),std::abs(b-r)));
        assert(std::isfinite(a)&&std::isfinite(b));if(!(std::abs(a-l)<1e-6f&&std::abs(b-r)<1e-6f)){std::cerr<<"divergence rate="<<rate<<" frame="<<i<<" error="<<maxError<<" reference="<<l<<" actual="<<a<<" idle="<<actual.idle()<<"\n";assert(false);}
        if(i<rate)assert(actual.idle()&&a==0&&b==0);
        if(i>rate*2&&i<rate*4)lateTail+=double(a)*a+double(b)*b;
        if(i>=rate)assert(!actual.idle());
        if(input!=0)assert(!actual.idle());
    }
    assert(lateTail>1e-7);
    DSP empty;empty.init(rate);empty.configure(p);float l,r;empty.process(0,p,l,r);assert(empty.idle()&&l==0&&r==0);
    std::cout<<"PASS: "<<rate<<"Hz "<<(longEcho?"1.9s echo":"short echo")<<" pristine idle,6s reverb+echo tail,resume equivalence maxError="<<maxError<<"\n";
}
static void reinitPreservesFilterState(int rate) {
    DSP reference,actual;reference.init(rate);actual.init(rate);
    float p[]={1,.55f,.4f,2.2f,.2f,280,.25f,4,-3,5,1,0,0,0,0,78,0,0,0,1};
    reference.configure(p);actual.configure(p);
    for(int i=0;i<100;i++){float l,r,a,b;reference.tick(.2f,p,l,r);actual.process(.2f,p,a,b);assert(l==a&&r==b);}
    reference.init(rate);actual.init(rate);reference.configure(p);actual.configure(p);
    assert(!actual.idle());
    for(int i=0;i<rate/10;i++){float input=i<1000?0.f:.1f;float l,r,a,b;reference.tick(input,p,l,r);actual.process(input,p,a,b);assert(l==a&&r==b);}
    std::cout<<"PASS: "<<rate<<"Hz reinit retains existing EQ state and remains sample-identical on silence/resume\n";
}
int main(){for(int rate:{44100,48000}){equivalent(rate,false);equivalent(rate,true);reinitPreservesFilterState(rate);}}
