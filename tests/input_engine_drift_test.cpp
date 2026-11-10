// Production Engine::render/openMicrophone, with finite hardware queues only.
#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <new>

static thread_local bool watchAlloc=false;static thread_local size_t callbackAllocations=0;
extern "C" void* __real_malloc(size_t);
extern "C" void* __real_calloc(size_t,size_t);
extern "C" void* __real_realloc(void*,size_t);
extern "C" void* __wrap_malloc(size_t n){if(watchAlloc)++callbackAllocations;return __real_malloc(n);}
extern "C" void* __wrap_calloc(size_t n,size_t size){if(watchAlloc)++callbackAllocations;return __real_calloc(n,size);}
extern "C" void* __wrap_realloc(void* p,size_t n){if(watchAlloc)++callbackAllocations;return __real_realloc(p,n);}
[[gnu::noinline]] void* operator new(std::size_t n){if(watchAlloc)++callbackAllocations;if(void* p=std::malloc(n))return p;throw std::bad_alloc();}
[[gnu::noinline]] void operator delete(void* p)noexcept{std::free(p);}
[[gnu::noinline]] void operator delete(void* p,std::size_t)noexcept{std::free(p);}
static float sourceSample(uint64_t frame){return .001f+float(frame)*1e-10f;}
struct Fixture {
    AAudioStream output;
    std::unique_ptr<Engine> e=std::make_unique<Engine>();
    std::array<float,32768> hardware{};
    std::array<float,8192> rendered{};
    std::array<float,4096> pitch{},voice{};
    uint64_t source=0,callbackFrames=0;
    int inRate,outRate,channels;
    Fixture(int in=48000,int out=48000,int ch=1):inRate(in),outRate(out),channels(ch){
        host_audio::openEnabled=true;host_audio::failCommunication=false;host_audio::actualPreset=0;
        host_audio::actualInputRate=in;host_audio::actualInputChannels=ch;
        output.rate=out;output.channels=2;e->output=&output;e->sr=out;
        e->dsp.init(out);e->feedback.init(out,false);e->composer.sampleRate(out);
        for(auto& p:e->params)p=0;e->params[0]=1;e->params[1]=1;e->params[3]=.3f;e->params[5]=280;e->params[15]=78;
        assert(e->openMicrophone(0,0));assert(e->inputRate==in&&e->inputChannels==ch);
        e->input->framesWritten=0;e->running=true;
    }
    ~Fixture(){watchAlloc=false;e.reset();host_audio::actualInputRate=host_audio::actualInputChannels=0;}
    void feed(size_t frames){
        AAudioStream* input=e->input;assert(input&&frames+size_t(input->frames)<=hardware.size()/channels);
        if(input->frames)std::memmove(hardware.data(),input->samples,size_t(input->frames)*channels*sizeof(float));
        for(size_t i=0;i<frames;i++)for(int c=0;c<channels;c++)hardware[(input->frames+i)*channels+c]=sourceSample(source+i);
        source+=frames;input->samples=hardware.data();input->frames+=int(frames);
        if(input->framesWritten>=0)input->framesWritten+=frames;
    }
    void callback(int frames){
        uint64_t before=e->frames.load();watchAlloc=true;assert(e->render(rendered.data(),frames)==AAUDIO_CALLBACK_RESULT_CONTINUE);watchAlloc=false;
        assert(e->frames==before+uint64_t(frames));callbackFrames+=frames;
        assert(e->pitchRing.pop(pitch.data(),frames)==size_t(frames));assert(e->composer.microphone.pop(voice.data(),frames)==size_t(frames));
        for(int i=0;i<frames;i++)assert(pitch[i]==e->inputBuffer[i]&&voice[i]==e->inputBuffer[i]);
    }
};
static void sameRateDefaultPath(){
    Fixture f;assert(f.e->inputResampler.driftState()==1);uint64_t heard=0;f.e->input->xrunCount=7;
    for(int block=0;block<100;block++){
        int n=std::array<int,5>{64,128,192,480,1024}[block%5];f.feed(n);int reads=f.e->input->readCalls;f.callback(n);
        assert(f.e->input->readCalls-reads<=3);assert(f.e->input->frames==0);
        for(int i=0;i<n;i++)if(f.e->inputBuffer[i]!=0){assert(f.e->inputBuffer[i]==sourceSample(heard));heard++;}
    }
    assert(f.source-heard==384&&f.e->inputResampler.primingSilenceFrames()==384);
    assert(f.e->inputResampler.correctionPpm()==0&&f.e->inputResampler.underflowFrames()==0);
    assert(callbackAllocations==0);
    assert(f.e->diagnostic[2]==7&&f.e->diagnostic[3]==0&&f.e->diagnostic[4]==0);
    assert(f.e->diagnostic[5]==0&&f.e->diagnostic[6]==0&&f.e->diagnostic[7]==0&&f.e->diagnostic[8]==2);
    assert(f.e->diagnostic[17]==384&&f.e->diagnostic[18]==48000);
    std::cout<<"PASS real Engine default same-rate mono enters adaptive FIFO; finite queue drained, zero-ppm bit-exact after8ms, analyzer/frame continuity, callbackalloc0\n";
}
static void finiteCounterBudget(){
    Fixture f;int calls=f.e->input->readCalls;f.callback(192);
    assert(f.e->input->readCalls==calls);for(int i=0;i<192;i++)assert(f.e->inputBuffer[i]==0);
    assert(f.e->diagnostic[3]==0&&f.e->diagnostic[4]==0&&f.e->diagnostic[8]==1);
    f.feed(8192);auto requested=f.e->input->totalRequested;f.callback(64);
    assert(f.e->input->totalRequested-requested<=64+2*384);
    assert(f.e->input->frames==8192-(f.e->input->framesRead));
    assert(f.e->input->framesWritten-f.e->input->framesRead==f.e->input->frames);
    assert(f.e->inputResampler.overflowFrames()==0&&callbackAllocations==0);
    std::cout<<"PASS actualavailable0 performs no speculative read; largefinite backlog drains only boundednominal+2target, no fakeoverflow/allocation\n";
}
static void counterFallbackAndShortReads(){
    Fixture f;f.e->input->framesWritten=-1;f.feed(1024);int before=f.e->input->readCalls;f.callback(1024);
    assert(f.e->input->readCalls>before&&f.e->input->readCalls-before<=3);
    assert(f.e->inputResampler.driftState()==2);
    // Missing read-request capacity is not lost audio: fallback deliberately
    // requests the extra reservoir while this finite queue contains one block.
    assert(f.e->diagnostic[3]==1&&f.e->diagnostic[4]==768&&f.e->diagnostic[5]==0);
    f.e->input->maxReadFrames=32;f.feed(256);before=f.e->input->readCalls;f.callback(256);
    assert(f.e->input->readCalls-before<=3);assert(f.e->running);assert(f.e->input->frames>0);
    // A successful zero-frame read is a bounded absence of data, not an error.
    f.e->input->maxReadFrames=0;before=f.e->input->readCalls;f.callback(1024);assert(f.e->input->readCalls-before<=3);
    assert(f.e->running&&f.e->fault==0);assert(f.e->inputResampler.underflowFrames()>0);
    bool zeros=false;for(int i=0;i<1024;i++)zeros|=f.e->inputBuffer[i]==0;assert(zeros);
    assert(f.e->diagnostic[5]==f.e->inputResampler.underflowFrames());assert(f.e->diagnostic[8]==1);
    std::cout<<"PASS unavailable hardware counters use bounded finite reads; short/zero reads do not invent samples or turn off a valid stream\n";
}
static void errorAndReopen(){
    Fixture f;f.feed(1024);f.callback(1024);f.e->input->readError=AAUDIO_ERROR_INTERNAL;f.feed(256);f.callback(256);
    assert(!f.e->running&&f.e->fault==AAUDIO_ERROR_INTERNAL&&f.e->params[11]==0);
    assert(f.e->diagnostic[8]==0&&f.e->diagnostic[7]==-1);
    f.e->microphoneOff();assert(f.e->publishedInput.load()==nullptr&&f.e->input==nullptr);
    host_audio::actualInputRate=44100;host_audio::actualInputChannels=2;f.e->running=true;
    assert(f.e->openMicrophone(0,0));assert(f.e->inputRate==44100&&f.e->inputChannels==2);
    assert(f.e->inputResampler.driftState()==1&&f.e->inputResampler.bufferedFrames()==0&&f.e->inputResampler.correctionPpm()==0);
    assert(f.e->inputResampler.underflowFrames()==0&&f.e->inputResampler.overflowFrames()==0);
    // Readback belongs to the newly opened route; no previous FIFO audio can leak.
    f.e->input->framesWritten=0;f.e->input->samples=nullptr;f.e->input->frames=0;f.callback(256);
    for(int i=0;i<256;i++)assert(f.e->inputBuffer[i]==0);
    f.e->microphoneOff();f.callback(256);for(int i=0;i<256;i++)assert(f.e->inputBuffer[i]==0);
    assert(f.e->diagnostic[2]==-1&&f.e->diagnostic[7]==-1&&f.e->diagnostic[8]==0&&f.e->diagnostic[17]==-1&&f.e->diagnostic[18]==-1);
    std::cout<<"PASS hardware read error fails closed; reopen44100/stereo resets FIFO/servo/counters; absent stream outputs silence without old audio\n";
}
static void differentActualFormat(){
    Fixture f(44100,48000,2);f.feed(941);
    for(int i=0;i<941;i++){f.hardware[i*2]=.25f;f.hardware[i*2+1]=-.75f;}
    f.e->input->xrunCount=3;f.callback(1024);
    for(int i=0;i<1024;i++)assert(std::abs(f.e->inputBuffer[i]-(i<384?0.f:-.25f))<1e-7f);
    assert(f.e->input->frames==0&&f.e->inputResampler.underflowFrames()==0);
    assert(f.e->diagnostic[2]==3&&f.e->diagnostic[8]==2);
    assert(f.e->diagnostic[17]==384&&f.e->diagnostic[18]==48000);
    assert(callbackAllocations==0);
    std::cout<<"PASS actual44100/stereo hardware despite48000/mono request; downmix/SRC,8ms warmup and output-unit diagnostic384/48000 truthful\n";
}
static void recordingFrameContinuity(const char* path){
    Fixture f;assert(f.e->beginRecord(path));
    for(int block=0;block<4;block++){if(block%2==0)f.feed(512);f.callback(512);}
    assert(f.e->recordFrames==2048&&f.e->frames==2048);assert(f.e->inputResampler.underflowFrames()>0);
    assert(f.e->diagnostic[3]==0&&f.e->diagnostic[5]>0); // No speculative hardware read, but real FIFO starvation.
    f.e->stopRecord();assert(!f.e->overflow&&!f.e->diskError);
    FILE* wav=std::fopen(path,"rb");assert(wav);assert(std::fseek(wav,0,SEEK_END)==0);assert(std::ftell(wav)==44+2048*4);std::fclose(wav);
    assert(callbackAllocations==0);
    std::cout<<"PASS real WAV writer retains2048 output/recordingframes across empty hardware/FIFO dropout/reprime; exact filelength,callbackalloc0\n";
}
static void actualEngineClockStep(){
    Fixture f;long double generated=0;uint64_t output=0,iteration=0;double maxSlew=0,old=0;
    while(output<48000u*120){
        int n=std::array<int,6>{64,128,192,256,480,1024}[iteration++%6];double ppm=output<48000u*60?200:-200;
        generated+=n*(1+ppm*1e-6L);size_t added=uint64_t(std::floor(generated))-f.source;f.feed(added);f.callback(n);
        double correction=f.e->inputResampler.correctionPpm();double slew=std::abs(correction-old)*48000/n;maxSlew=std::max(maxSlew,slew);old=correction;
        assert(slew<=50+1e-6&&std::abs(correction)<=1000);assert(f.e->inputResampler.underflowFrames()==0&&f.e->inputResampler.overflowFrames()==0);
        assert(f.e->input->framesWritten-f.e->input->framesRead==f.e->input->frames);assert(f.e->input->frames==0);
        output+=n;
    }
    assert(std::abs(f.e->inputResampler.correctionPpm()+200)<20);assert(callbackAllocations==0);
    std::cout<<"PASS productioncallback120s synthetic finite hardware clock +200->-200ppm step; correction="<<old<<"ppm maxSlew="<<maxSlew<<"ppm/s; no under/overflow,callbackalloc0 (not USB measurement)\n";
}
int main(int argc,char** argv){assert(argc==2);sameRateDefaultPath();finiteCounterBudget();counterFallbackAndShortReads();errorAndReopen();differentActualFormat();recordingFrameContinuity(argv[1]);actualEngineClockStep();}
