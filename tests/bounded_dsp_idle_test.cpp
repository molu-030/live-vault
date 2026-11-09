#include "../app/src/main/cpp/audio_core.h"
#include <cassert>
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <new>
#include <ctime>
static bool watchAlloc=false;
static size_t allocations=0;
void* operator new(std::size_t n){if(watchAlloc)++allocations;if(void* p=std::malloc(n))return p;throw std::bad_alloc();}
void operator delete(void* p) noexcept{std::free(p);}
void operator delete(void* p,std::size_t) noexcept{std::free(p);}
using namespace lm;
static volatile float sink;
static void certificate(){
    float values[65]={};BoundedTail scan;auto span=[&](size_t){return std::pair<const float*,size_t>{values,65};};
    values[64]=1;for(int i=0;i<2000;i++)assert(!scan.step(true,1,span));
    values[64]=0;bool done=false;for(int i=0;i<2000&&!done;i++)done=scan.step(true,1,span);assert(done);
    scan.init();for(int i=0;i<100;i++)assert(!scan.step(true,1,span));
    values[0]=.3f;assert(!scan.step(false,1,span));for(int i=0;i<1000;i++)assert(!scan.step(true,1,span));
    values[0]=std::numeric_limits<float>::quiet_NaN();for(int i=0;i<100;i++)assert(!scan.step(true,1,span));
    std::cout<<"PASS bounded certificate hidden slot, intervening-write invalidation, NaN\n";
}
static void equivalent(int rate,float feedback){
    DSP ref,actual;ref.init(rate);actual.init(rate);
    float p[]={1,.55f,.65f,6,.7f,1900,feedback,9,-6,12,1,0,0,0,0,78,0,0,0,0};
    float maxError=0;int retired=-1;size_t maxVisits=0;double audibleTail=0;
    // Maximum feedback takes more than 200 seconds to decay below 1e-18.
    const int resume=rate*310,end=resume+rate*4;
    for(int at=0;at<end;at+=512){
        if(at>rate*305){p[5]=37+(at/512%3)*400;p[3]=.3f+(at/512%4)*1.8f;p[18]=at/512%4;p[7]=-12;p[8]=9;p[9]=-6;p[10]=at/512%2;p[19]=at/512%2;}
        ref.configure(p);actual.configure(p);size_t visits=actual.tailInspected();
        watchAlloc=true;
        for(int i=at;i<std::min(end,at+512);++i){
            // Separated sparse impulses expose echoes hidden between output peaks.
            float x=(i==rate||i==rate+71||i==rate*3+19)?.7f:0.f;
            if(i>=resume&&i<resume+rate/8)x=.22f*std::sin(float(2*PI*223*i/rate));
            float a,b,l,r;ref.tick(x,p,l,r);actual.process(x,p,a,b);
            float error=std::max(std::abs(a-l),std::abs(b-r));maxError=std::max(maxError,error);
            if(!(std::isfinite(a)&&std::isfinite(b)&&error<=1e-12f)){
                std::cerr<<"FAIL frame="<<i<<" rate="<<rate<<" fb="<<feedback<<" error="<<error<<" idle="<<actual.idle()<<"\n";std::abort();}
            if(i>rate*4&&i<rate*6)audibleTail+=double(a)*a+double(b)*b;
            if(i>rate*4&&actual.idle()&&retired<0)retired=i;
            if(x!=0)assert(!actual.idle());
            if(i>=rate&&i<rate*4)assert(!actual.idle());
        }
        watchAlloc=false;
        maxVisits=std::max(maxVisits,actual.tailInspected()-visits);
        assert(actual.tailInspected()-visits<=256);
    }
    if(!(retired>0&&retired<resume))std::cerr<<"NO RETIRE rate="<<rate<<" feedback="<<feedback<<" inspected="<<actual.tailInspected()<<"\n";
    assert(retired>0&&retired<resume);assert(audibleTail>1e-8);assert(allocations==0);
    // Reinitialization retains EQ history exactly as before this optimization.
    ref.init(rate);actual.init(rate);ref.configure(p);actual.configure(p);
    for(int i=0;i<rate;i++){float a,b,l,r,x=i<rate/2?0.f:.1f;ref.tick(x,p,l,r);actual.process(x,p,a,b);assert(std::abs(a-l)<=1e-12f&&std::abs(b-r)<=1e-12f);}
    std::cout<<"PASS "<<rate<<"Hz fb="<<feedback<<" 6s reverb/1.9s echo sparse/resume/reinit maxError="<<maxError<<" retiredSec="<<double(retired)/rate<<" maxScanSlotsPer512="<<maxVisits<<" callbackAllocations="<<allocations<<"\n";
}
static void exceptional(){
    float p[]={1,.55f,.4f,6,.4f,1900,.7f,0,0,0,1,0,0,0,0,78,0,0,0,1};DSP d;d.init(48000);d.configure(p);float a,b;
    d.process(std::numeric_limits<float>::denorm_min(),p,a,b);
    for(int i=0;i<48000*10;i++)d.process(0,p,a,b);
    assert(d.idle());
    d.process(std::numeric_limits<float>::quiet_NaN(),p,a,b);
    for(int i=0;i<48000*4;i++)d.process(0,p,a,b);
    assert(!d.idle());
    d.init(48000);d.configure(p);d.process(0,p,a,b);assert(!d.idle()); // EQ NaN retained by existing init semantics.
    DSP invalid;invalid.init(48000);p[4]=std::numeric_limits<float>::quiet_NaN();invalid.configure(p);
    invalid.process(0,p,a,b);assert(!invalid.idle()&&!std::isfinite(a));
    DSP oversized;oversized.init(48000);p[4]=.4f;p[0]=1e30f;oversized.configure(p);
    oversized.process(0,p,a,b);assert(!oversized.idle());
    std::cout<<"PASS denormal retirement, NaN state/config and out-of-range gain admission\n";
}
static double cpuMicros(){timespec t{};clock_gettime(CLOCK_THREAD_CPUTIME_ID,&t);return t.tv_sec*1e6+t.tv_nsec*.001;}
static void bench(){
    constexpr int blocks=3000,n=512;float p[]={1,.55f,.4f,6,.4f,1900,.7f,0,0,0,1,0,0,0,0,78,0,0,0,1};
    for(int scenario:{0,1,2})for(bool optimized:{false,true}){
        DSP d;d.init(48000);d.configure(p);
        if(scenario==2)for(int i=0;i<48000*170;i++){float a,b;d.process(i==0?.7f:0.f,p,a,b);}
        double total=0,worstWall=0;std::array<double,blocks> cpu{};size_t maxVisits=0;
        for(int block=0;block<blocks;block++){
            auto begin=std::chrono::steady_clock::now();double cpuBegin=cpuMicros();size_t visits=d.tailInspected();
            for(int j=0;j<n;j++){float a,b,x=scenario==0?.1f*std::sin(float((block*n+j)*.03)):0.f;if(optimized)d.process(x,p,a,b);else d.tick(x,p,a,b);sink=a+b;}
            cpu[block]=cpuMicros()-cpuBegin;maxVisits=std::max(maxVisits,d.tailInspected()-visits);
            double us=std::chrono::duration<double,std::micro>(std::chrono::steady_clock::now()-begin).count();total+=cpu[block];worstWall=std::max(worstWall,us);
        }
        if(scenario==2&&optimized){assert(maxVisits>0);assert(d.idle());}
        std::sort(cpu.begin(),cpu.end());
        std::cout<<"HOST benchmark "<<(scenario==0?"active voice":scenario==1?"pristine idle":"late tail/certificate")<<" "<<(optimized?"process":"tick reference")<<" CPU512us mean="<<total/blocks<<" p50="<<cpu[blocks/2]<<" p99="<<cpu[blocks*99/100]<<" max="<<cpu.back()<<" wallMax="<<worstWall<<" maxScanSlots="<<maxVisits<<" (host only; wall includes scheduling)\n";
    }
}
int main(){certificate();for(int rate:{44100,48000})for(float fb:{-.7f,.7f})equivalent(rate,fb);exceptional();bench();}
