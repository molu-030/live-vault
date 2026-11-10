// Runs the real Engine provider-selection, render and writer branches. Only the
// hardware-facing OboeBackend is replaced; this is not a device/latency test.
#define LV_TEST_OBOE 1
#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <cstdlib>
#include <iostream>
#include <new>

static thread_local bool watchCallback=false;
static thread_local size_t allocations=0,locks=0;
extern "C" void* __real_malloc(size_t);
extern "C" void* __real_calloc(size_t,size_t);
extern "C" void* __real_realloc(void*,size_t);
extern "C" int __real_pthread_mutex_lock(pthread_mutex_t*);
extern "C" int __real_pthread_mutex_trylock(pthread_mutex_t*);
extern "C" void* __wrap_malloc(size_t n){if(watchCallback)++allocations;return __real_malloc(n);}
extern "C" void* __wrap_calloc(size_t n,size_t s){if(watchCallback)++allocations;return __real_calloc(n,s);}
extern "C" void* __wrap_realloc(void* p,size_t n){if(watchCallback)++allocations;return __real_realloc(p,n);}
extern "C" int __wrap_pthread_mutex_lock(pthread_mutex_t* p){if(watchCallback)++locks;return __real_pthread_mutex_lock(p);}
extern "C" int __wrap_pthread_mutex_trylock(pthread_mutex_t* p){if(watchCallback)++locks;return __real_pthread_mutex_trylock(p);}
[[gnu::noinline]] void* operator new(std::size_t n){if(watchCallback)++allocations;if(auto* p=std::malloc(n))return p;throw std::bad_alloc();}
[[gnu::noinline]] void operator delete(void* p)noexcept{std::free(p);}
[[gnu::noinline]] void operator delete(void* p,std::size_t)noexcept{std::free(p);}
namespace fake_oboe {
int outputOpenError=0,inputOpenError=0,outputStartError=0,inputStartError=0,opens=0,reads=0;
bool available=true;
AAudioStream input;
void reset(){outputOpenError=inputOpenError=outputStartError=inputStartError=opens=reads=0;available=true;input=AAudioStream{};input.channels=1;input.rate=48000;}
}
namespace lv {
class OboeBackend::Impl {
public:
    Render render;Error error;void* user;bool output=false,input=false,started=false;int bursts=2;
    Impl(Render r,Error e,void* u):render(r),error(e),user(u){}
};
OboeBackend::OboeBackend(Render r,Error e,void* u):impl_(new Impl(r,e,u)){}
OboeBackend::~OboeBackend()=default;
int32_t OboeBackend::openOutput(int32_t,int){++fake_oboe::opens;if(fake_oboe::outputOpenError)return fake_oboe::outputOpenError;impl_->output=true;return 0;}
int32_t OboeBackend::openInput(int32_t,int32_t,bool,bool,BuiltinMic){if(fake_oboe::inputOpenError)return fake_oboe::inputOpenError;impl_->input=true;return 0;}
int32_t OboeBackend::startOutput(){return fake_oboe::outputStartError;}
int32_t OboeBackend::startInput(){if(fake_oboe::inputStartError)return fake_oboe::inputStartError;impl_->started=true;return 0;}
void OboeBackend::closeOutput(){impl_->output=false;}
void OboeBackend::closeInput(){impl_->input=impl_->started=false;}
int32_t OboeBackend::readInput(float* p,int32_t n){++fake_oboe::reads;return AAudioStream_read(&fake_oboe::input,p,n,0);}
int32_t OboeBackend::availableInputFrames()const{return fake_oboe::available?fake_oboe::input.frames:-1;}
bool OboeBackend::setOutputBufferBursts(int32_t n){impl_->bursts=n;return impl_->output;}
OboeBackend::StreamInfo OboeBackend::outputInfo()const{StreamInfo i;if(impl_->output){i.deviceId=20;i.sampleRate=48000;i.channels=2;i.format=2;i.framesPerBurst=192;i.bufferCapacity=4096;i.bufferSize=192*impl_->bursts;i.xruns=7;i.platformApi=2;}return i;}
OboeBackend::StreamInfo OboeBackend::inputInfo()const{StreamInfo i;if(impl_->input){i.deviceId=10;i.sampleRate=fake_oboe::input.rate;i.channels=fake_oboe::input.channels;i.format=2;i.inputPreset=AAUDIO_INPUT_PRESET_VOICE_RECOGNITION;i.sessionId=42;i.platformApi=2;}return i;}
bool OboeBackend::hasOutput()const{return impl_->output;}
bool OboeBackend::hasInput()const{return impl_->started;}
}
struct Fixture {
    std::unique_ptr<Engine> e=std::make_unique<Engine>();
    std::array<float,8192> hardware{},out{};
    Fixture(){fake_oboe::reset();host_audio::openEnabled=true;host_audio::failCommunication=false;host_audio::actualInput=10;host_audio::actualOutput=20;}
    ~Fixture(){watchCallback=false;}
    void start(bool mic=true){e->requestedBackend=1;assert(e->start(0,0,0,mic));}
    void quietAnalyzers(){e->running=false;if(e->analyzer.joinable())e->analyzer.join();if(e->voiceAnalyzer.joinable())e->voiceAnalyzer.join();e->running=true;}
    void feed(int n){hardware.fill(.1f);fake_oboe::input.samples=hardware.data();fake_oboe::input.frames=n;}
    void render(int n){watchCallback=true;assert(e->render(out.data(),n)==AAUDIO_CALLBACK_RESULT_CONTINUE);watchCallback=false;}
};
static void selection(){
    assert(engine.requestedBackend==0&&engine.appliedBackend==-1&&engine.platformApi==-1);
    auto request=[](int value){return Java_pro_livemic_studio_NativeAudio_requestBackend(nullptr,nullptr,value);};
    assert(!request(-1)&&!request(2));assert(request(1)&&engine.requestedBackend==1);
    for(auto* flag:{&engine.running,&engine.recording,&engine.writerRunning,&engine.sourceRecording}){*flag=true;assert(!request(0));*flag=false;}
    engine.playback.configure(true,1);assert(!request(0));engine.playback.configure(false,1);
    host_audio::openEnabled=true;assert(engine.start(0,0,0,false));assert(!request(0));
    engine.stop();assert(request(0)&&engine.requestedBackend==0);
    std::cout<<"PASS native defaults and invalid/live/recording/writer/capture/playback provider rejection\n";
}
static void initialFallbacks(){
    {Fixture f;assert(f.e->start(0,0,0,false));assert(fake_oboe::opens==0&&f.e->appliedBackend==0&&f.e->platformApi==0);}
    for(int failure=1;failure<=4;failure++){
        Fixture f;
        if(failure==1)fake_oboe::outputOpenError=-100;
        if(failure==2)fake_oboe::inputOpenError=-101;
        if(failure==3)fake_oboe::outputStartError=-102;
        if(failure==4)fake_oboe::inputStartError=-103;
        f.start();assert(f.e->requestedBackend==1&&f.e->appliedBackend==0&&f.e->platformApi==0);
        int reason=failure<3?failure:3;assert(f.e->backendFallback==reason&&f.e->fault==0&&f.e->input&&f.e->output&&f.e->running);
        assert(!f.e->oboe->hasOutput()&&!f.e->oboe->hasInput());
        assert(f.e->start(0,0,0,true));assert(f.e->backendFallback==reason);
        f.e->stop();assert(f.e->appliedBackend==-1&&f.e->platformApi==-1);
    }
    {Fixture f;fake_oboe::outputOpenError=-100;host_audio::openEnabled=false;f.e->requestedBackend=1;
        assert(!f.e->start(0,0,0));assert(f.e->requestedBackend==1&&f.e->appliedBackend==-1&&f.e->platformApi==-1&&f.e->backendFallback==1&&!f.e->running);}
    std::cout<<"PASS real initial Oboe output-open/input-open/output-start/input-start failures retry complete AAudio; requested/applied/reason truth survives repeated start; failed fallback is unapplied\n";
}
static void failedMicJoin(){
    for(bool failStart:{false,true}){
        Fixture f;f.start(false);f.quietAnalyzers();f.e->frames=123456;f.e->transport=9.5;
        f.e->playback.configure(true,1);float samples[]={.2f,.3f};f.e->playback.push(samples,2);
        f.e->localDesiredPlay=true;f.e->localPlaying=true;f.e->localCursor=777;
        if(failStart)fake_oboe::inputStartError=-102;else fake_oboe::inputOpenError=-101;
        assert(!f.e->start(0,0,0,true));
        assert(f.e->usingOboe()&&f.e->running&&!f.e->oboe->hasInput()&&f.e->frames==123456&&f.e->transport==9.5);
        assert(f.e->playback.isEnabled()&&f.e->localDesiredPlay&&f.e->localPlaying&&f.e->localCursor==777);
        float read[2]{};f.e->playback.read(read,2);assert(read[0]==.2f&&read[1]==.3f);
        assert(f.e->backendFallback==(failStart?3:2));
    }
    std::cout<<"PASS failed microphone join retains Oboe backing output, captured/local playback and transport/frame clocks\n";
}
static void renderAndReopen(const char* path){
    Fixture f;f.start();f.quietAnalyzers();assert(f.e->usingOboe()&&f.e->platformApi==0&&f.e->input==nullptr);
    assert(f.e->inputDeviceSnapshot==10&&f.e->inputSessionSnapshot==42&&f.e->inputProfile==1);
    f.e->refreshOboeSnapshots();assert(f.e->outputXrunsSnapshot==7&&f.e->outputBufferSnapshot==384);
    assert(f.e->oboe->setOutputBufferBursts(3));f.e->refreshOboeSnapshots();assert(f.e->outputBufferSnapshot==576);
    f.e->harmony.configure(true,0,0,0,.5f,1,.5f);f.e->frequency=440;f.e->pitchConfidence=.95f;f.e->pitchLevel=.1f;
    f.feed(1024);f.render(1024);assert(f.e->harmony.inputNote==69&&f.e->harmony.note1>=0&&f.e->harmony.level==.1f);
    assert(f.e->inputBuffer[383]==0&&f.e->inputBuffer[384]==.1f&&f.e->frames==1024&&fake_oboe::reads==1);
    std::array<float,1024> ring{};assert(f.e->pitchRing.pop(ring.data(),1024)==1024);assert(ring[384]==.1f);
    assert(f.e->composer.microphone.pop(ring.data(),1024)==1024&&ring[384]==.1f);
    int reads=fake_oboe::reads;f.render(512);assert(fake_oboe::reads==reads&&f.e->inputResampler.underflowFrames()>0);
    fake_oboe::available=false;f.render(512);assert(f.e->inputShortReads>0&&f.e->inputMissingFrames>0);
    f.e->microphoneOff();assert(!f.e->oboe->hasInput()&&f.e->inputDeviceSnapshot==0);
    fake_oboe::input.rate=44100;fake_oboe::input.channels=2;assert(f.e->openMicrophone(0,0));
    assert(f.e->inputRate==44100&&f.e->inputChannels==2&&f.e->inputShortReads==0&&f.e->inputMissingFrames==0);
    assert(f.e->inputResampler.underflowFrames()==0&&f.e->inputResampler.correctionPpm()==0);
    fake_oboe::available=true;f.feed(941);for(int i=0;i<941;i++){f.hardware[i*2]=.25f;f.hardware[i*2+1]=-.75f;}f.render(1024);
    assert(f.e->inputBuffer[383]==0&&f.e->inputBuffer[384]==-.25f);
    assert(f.e->beginRecord(path));
    for(int i=0;i<4;i++){f.feed(470);f.render(512);}assert(f.e->recordFrames==2048);f.e->stopRecord();
    FILE* wav=std::fopen(path,"rb");assert(wav);std::fseek(wav,0,SEEK_END);assert(std::ftell(wav)==44+2048*4);std::fclose(wav);
    fake_oboe::input.readError=-123;f.feed(256);f.render(256);assert(!f.e->running&&f.e->fault==-123&&f.e->params[11]==0&&f.e->diagnostic[8]==0);
    assert(allocations==0&&locks==0);
    std::cout<<"PASS real Oboe Engine render shares adaptive FIFO/downmix/SRC, Harmony pitch, analyzers and exact 2048-frame WAV writer; counter reset on reopen; read-error fail-closed; callback malloc/new/mutex/trylock=0\n";
}
int main(int argc,char** argv){assert(argc==2);selection();initialFallbacks();failedMicJoin();renderAndReopen(argv[1]);}
