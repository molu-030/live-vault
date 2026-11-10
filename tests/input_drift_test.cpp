#include "../app/src/main/cpp/input_resampler.h"
#include <cassert>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <new>
#include <vector>
static bool watch=false;static size_t allocations=0;
[[gnu::noinline]] void* operator new(std::size_t n){if(watch)++allocations;if(void* p=std::malloc(n))return p;throw std::bad_alloc();}
[[gnu::noinline]] void operator delete(void* p)noexcept{std::free(p);}
[[gnu::noinline]] void operator delete(void* p,std::size_t)noexcept{std::free(p);}
static float sample(uint64_t index){return .1f+float(index)*1e-9f;}
static void exactZero(){
 for(int rate:{44100,48000})for(int channels:{1,2}){
  auto r=std::make_unique<lm::InputResampler>();r->reset(rate,rate,true);uint64_t source=0,heard=0;std::array<float,8192> in{},out{};
  for(int block=0;block<10000;block++){
   int n=std::array<int,8>{64,128,192,256,480,512,960,1024}[block%8];
   for(int i=0;i<n;i++)for(int c=0;c<channels;c++)in[i*channels+c]=sample(source+i);
   assert(r->push(in.data(),n,channels)==size_t(n));source+=n;r->render(out.data(),n);
   for(int i=0;i<n;i++)if(out[i]!=0){assert(out[i]==sample(heard));heard++;}
   assert(r->correctionPpm()==0&&r->underflowFrames()==0);
  }
  assert(source-heard==uint64_t(std::lround(rate*.008)));assert(r->primingSilenceFrames()==uint64_t(std::lround(rate*.008)));assert(r->driftState()==2);
 }
 std::cout<<"PASS 44.1/48k zero-ppm mono/stereo sample-exact; variable callbacks; startup353/384 silence frames (~8ms)\n";
}
static void nominalConversion(){
 for(auto rates:{std::pair<int,int>{44100,48000},{48000,44100}}){
  auto r=std::make_unique<lm::InputResampler>();r->reset(rates.first,rates.second,true);std::array<float,8192> input{},output{};
  uint64_t source=0,heard=0;long double generated=0;
  for(int block=0;block<10000;block++){
   int n=std::array<int,5>{64,127,256,480,1024}[block%5];generated+=static_cast<long double>(n)*rates.first/rates.second;
   size_t got=uint64_t(std::floor(generated))-source;for(size_t i=0;i<got;i++)input[i]=sample(source+i);source+=got;
   r->push(input.data(),got,1);r->render(output.data(),n);
   for(int i=0;i<n;i++)if(output[i]!=0){double position=double(heard)*rates.first/rates.second;size_t a=size_t(position);float expected=float(sample(a)+(sample(a+1)-sample(a))*(position-a));assert(std::abs(output[i]-expected)<3e-8f);heard++;}
   assert(r->correctionPpm()==0&&r->underflowFrames()==0);
  }
 }
 auto r=std::make_unique<lm::InputResampler>();r->reset(48000,48000,true);std::array<float,2048> stereo{};std::array<float,1024> output{};
 for(int i=0;i<1024;i++){stereo[2*i]=.25f;stereo[2*i+1]=-.75f;}r->push(stereo.data(),1024,2);r->render(output.data(),1024);
 for(int i=0;i<1024;i++)assert(output[i]==(i<384?0.f:-.25f));
 std::cout<<"PASS adaptive nominal44.1/48conversion matches fixed interpolation with0ppm; unequal stereo downmix exact\n";
}
static void clock(int inputRate,int outputRate,double ppm){
 auto r=std::make_unique<lm::InputResampler>();r->reset(inputRate,outputRate,true);
 std::array<float,8192> input{},output{};uint64_t source=0,dest=0,callback=0;long double produced=0;
 double minFill=1e20,maxFill=0,oldPpm=0,maxSlew=0;float previous=0;uint64_t audible=0;
 while(dest<uint64_t(outputRate)*600){
  int n=std::array<int,8>{64,128,192,256,480,512,960,1024}[callback++%8];
  produced+=static_cast<long double>(n)*inputRate/outputRate*(1+ppm*1e-6L);
  size_t got=uint64_t(std::floor(produced))-source;assert(got<=4096);
  for(size_t i=0;i<got;i++){input[i*2]=sample(source+i)+.025f;input[i*2+1]=sample(source+i)-.025f;}
  watch=true;assert(r->push(input.data(),got,2)==got);source+=got;r->render(output.data(),n);watch=false;
  double delta=std::abs(r->correctionPpm()-oldPpm),allowed=lm::InputDrift::slewPpmPerSecond*n/outputRate;
  assert(delta<=allowed+1e-8);maxSlew=std::max(maxSlew,delta*outputRate/n);oldPpm=r->correctionPpm();
  for(int i=0;i<n;i++)if(output[i]!=0){if(audible)assert(std::abs(output[i]-previous)<1e-6f);previous=output[i];audible++;}
  if(dest>uint64_t(outputRate)*60){minFill=std::min(minFill,double(r->bufferedFrames()));maxFill=std::max(maxFill,double(r->bufferedFrames()));}
  assert(std::abs(r->correctionPpm())<=lm::InputDrift::limitPpm);dest+=n;
 }
 assert(r->underflowFrames()==0&&r->overflowFrames()==0&&allocations==0);
 assert(std::abs(r->correctionPpm()-ppm)<20);assert(minFill>inputRate*.003&&maxFill<inputRate*.015);
 std::cout<<"PASS 600s synthetic "<<inputRate<<"->"<<outputRate<<" clock="<<ppm<<"ppm correction="<<r->correctionPpm()<<"ppm fifo="<<minFill<<".."<<maxFill<<" maxSlew="<<maxSlew<<"ppm/s under/overflow=0 alloc=0\n";
}
static void tonePitch(double ppm){
 auto r=std::make_unique<lm::InputResampler>();r->reset(48000,48000,true);std::array<float,2048> input{},output{};
 uint64_t source=0,dest=0,crossings=0;long double generated=0;double first=0,last=0;float previous=0;
 while(dest<48000u*120){
  int n=480;generated+=n*(1+ppm*1e-6L);size_t got=uint64_t(std::floor(generated))-source;
  for(size_t i=0;i<got;i++)input[i]=float(.2*std::sin(2*3.141592653589793*440*(source+i)/(48000*(1+ppm*1e-6))));
  r->push(input.data(),got,1);source+=got;r->render(output.data(),n);
  for(int i=0;i<n;i++){float next=output[i];if(dest+i>48000u*90&&previous<=0&&next>0){double at=double(dest+i)-next/(next-previous);if(crossings++==0)first=at;last=at;}previous=next;}dest+=n;
 }
 double hz=(crossings-1)*48000.0/(last-first);assert(std::abs(hz-440)<.02&&r->underflowFrames()==0);
 std::cout<<"PASS physical440Hz at synthetic "<<ppm<<"ppm capture clock -> output="<<hz<<"Hz after settling\n";
}
static void dropoutReset(){
 auto r=std::make_unique<lm::InputResampler>();r->reset(48000,48000,true);std::array<float,1024> input{},out{};input.fill(.25f);
 r->push(input.data(),1024,1);r->render(out.data(),1024);assert(r->driftState()==2);
 r->render(out.data(),1024);assert(r->underflowFrames()==640&&r->starvationEvents()==1&&r->driftState()==1);
 for(int i=384;i<1024;i++)assert(out[i]==0);
 assert(r->correctionPpm()==0);
 r->reset(44100,48000,true);assert(r->bufferedFrames()==0&&r->underflowFrames()==0&&r->correctionPpm()==0);
 r->render(out.data(),1024);for(float v:out)assert(v==0);assert(r->primingSilenceFrames()==1024);
 std::vector<float> many(140000,.2f);size_t accepted=r->push(many.data(),many.size(),1);assert(accepted==131072&&r->overflowFrames()==many.size()-accepted);
 r->reset(48000,44100,true);assert(r->overflowFrames()==0&&r->driftState()==1);
 std::cout<<"PASS starvation silence/reprime, overflow frame accounting and route/rate reset; output callback frame count unchanged\n";
}
int main(){exactZero();nominalConversion();for(auto rates:{std::pair<int,int>{48000,48000},{44100,44100},{44100,48000},{48000,44100}})for(double ppm:{-500.,500.})clock(rates.first,rates.second,ppm);tonePitch(-500);tonePitch(500);dropoutReset();}
