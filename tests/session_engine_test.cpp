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

extern "C" size_t __real_fread(void*,size_t,size_t,FILE*);
extern "C" size_t __real_fwrite(const void*,size_t,size_t,FILE*);
extern "C" int __real_fseeko(FILE*,off_t,int);
static thread_local size_t ioCalls=0;
extern "C" size_t __wrap_fread(void* p,size_t s,size_t n,FILE* f){if(watchCallback)ioCalls++;return __real_fread(p,s,n,f);}
extern "C" size_t __wrap_fwrite(const void* p,size_t s,size_t n,FILE* f){if(watchCallback)ioCalls++;return __real_fwrite(p,s,n,f);}
extern "C" int __wrap_fseeko(FILE* f,off_t o,int w){if(watchCallback)ioCalls++;return __real_fseeko(f,o,w);}
int main(int argc,char** argv){assert(argc==2);auto e=std::make_unique<Engine>();e->sr=e->inputRate=48000;e->dsp.init(48000);e->feedback.init(48000,false);e->running=true;
 auto p=std::make_unique<SessionTransport::Plan>();p->duration=48000;SessionTransport::Clip c;c.track=0;c.length=48000;c.source=std::make_unique<LocalPcmSource>();std::string bed=std::string(argv[1])+".bed.wav";FILE* bedFile=fopen(bed.c_str(),"wb");assert(bedFile);std::vector<float> bedPcm(96000,.2f);sourceHeader(bedFile,48000,2,96000*4);assert(fwrite(bedPcm.data(),4,bedPcm.size(),bedFile)==bedPcm.size());fclose(bedFile);assert(c.source->openWav(bed.c_str()));auto* cache=c.source.get();p->clips.push_back(std::move(c));assert(e->session.prepare(std::move(p)));e->session.setEnabled(true);e->sessionRecordBus=true;e->recordSyncMs=250;assert(e->session.armCapture(7));assert(e->beginRecord(argv[1]));assert(e->alignment.vocalFrames==0);float out[512];e->render(out,256);assert(e->recordFrames==0);int64_t before[11];e->session.state(before);assert(before[8]==-1&&before[9]==0);assert(e->session.transport(1));auto waitCache=[&]{auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(1);while(!cache->parkedOnCache()&&std::chrono::steady_clock::now()<deadline)std::this_thread::yield();assert(cache->parkedOnCache());};waitCache();auto cacheReads=cache->readOperations(),cacheWakes=cache->cacheWakeups();std::this_thread::sleep_for(std::chrono::milliseconds(40));assert(cache->readOperations()==cacheReads&&cache->cacheWakeups()==cacheWakes);
 for(int block=0;block<24;block++){e->params[2]=block%2;e->params[3]=8;e->params[4]=1;e->params[7]=8;e->params[8]=-12;e->params[9]=5;watchCallback=true;e->render(out,256);watchCallback=false;for(float v:out)assert(std::abs(v-.2f)<1e-6);for(int i=0;i<512;i++)assert(e->recordBuffer[i]==0);if(block<8)assert(cache->cacheWakeups()==cacheWakes);}
 assert(allocations==0&&locks==0&&ioCalls==0);waitCache();cacheWakes=cache->cacheWakeups();int64_t state[11];e->session.state(state);assert(state[8]==0&&state[9]==6144&&e->recordFrames==6144);e->session.finishCapture();e->render(out,256);assert(e->recordFrames==6144);cache->prefetch(8192);auto wakeDeadline=std::chrono::steady_clock::now()+std::chrono::seconds(1);while(cache->cacheWakeups()==cacheWakes&&std::chrono::steady_clock::now()<wakeDeadline)std::this_thread::yield();assert(cache->cacheWakeups()>cacheWakes);waitCache();assert(cache->readOperations()>cacheReads);assert(e->session.transport(2));assert(e->session.seek(1000));assert(e->session.transport(1));waitCache();std::atomic<bool> go{false};std::thread sessionStop([&]{while(!go.load())std::this_thread::yield();e->stopRecord();});std::thread legacyStop([&]{while(!go.load())std::this_thread::yield();e->stop();});go=true;sessionStop.join();legacyStop.join();assert(!e->writer.joinable()&&!e->recording&&!e->running);puts("PASS real callback session fixed-bed FX isolation, voice-only record bus, exact capture anchor, allocation/lock/fileIO=0; cache ready/page wake/seek/resume park; concurrent session/legacy stop serialized");}
