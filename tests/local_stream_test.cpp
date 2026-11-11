#include <cstdlib>
#include <new>
#include <cstdio>
#include <atomic>
#include <thread>
#include <chrono>
#include <sys/types.h>
static thread_local bool inCallback=false;
static size_t callbackAllocations=0,callbackFileOperations=0;
static std::atomic<bool> blockRead{false},readEntered{false},releaseRead{false};
void* operator new(std::size_t n){if(inCallback)++callbackAllocations;if(void* p=std::malloc(n))return p;throw std::bad_alloc();}
void operator delete(void* p)noexcept{std::free(p);}void operator delete(void* p,std::size_t)noexcept{std::free(p);}
extern "C" size_t __real_fread(void*,size_t,size_t,FILE*);
extern "C" size_t __wrap_fread(void* p,size_t a,size_t b,FILE* f){if(inCallback)++callbackFileOperations;if(blockRead.load()){readEntered=true;while(!releaseRead.load())std::this_thread::sleep_for(std::chrono::milliseconds(1));}return __real_fread(p,a,b,f);}
extern "C" size_t __real_fwrite(const void*,size_t,size_t,FILE*);
extern "C" size_t __wrap_fwrite(const void* p,size_t a,size_t b,FILE* f){if(inCallback)++callbackFileOperations;return __real_fwrite(p,a,b,f);}
extern "C" int __real_fseeko(FILE*,off_t,int);
extern "C" int __wrap_fseeko(FILE* f,off_t o,int w){if(inCallback)++callbackFileOperations;return __real_fseeko(f,o,w);}
#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <iostream>
#include <memory>
#include <fcntl.h>
static void callback(Engine& e,float* out,int frames){inCallback=true;e.render(out,frames);inCallback=false;}
static int16_t sample(uint64_t i,int c){return int16_t(int((i*17+c*7001)%24001)-12000);}
static void pcm(const std::string& path,size_t frames,int channels){FILE* f=fopen(path.c_str(),"wb");assert(f);for(size_t i=0;i<frames;i++)for(int c=0;c<channels;c++){int16_t v=sample(i,c);assert(fwrite(&v,2,1,f)==1);}assert(fclose(f)==0);}
static std::unique_ptr<Engine> setup(int rate){auto e=std::make_unique<Engine>();e->sr=e->inputRate=rate;e->dsp.init(rate);e->feedback.init(rate,false);e->composer.sampleRate(rate);e->params[1]=1;return e;}
static void run(const std::string& dir,int rate,int channels){
 std::string path=dir+"/stream-"+std::to_string(rate)+"-"+std::to_string(channels)+".pcm";pcm(path,rate*2,channels);auto e=setup(rate);
 assert(e->loadLocalFile(path,rate,channels));assert(e->localSource->frames()==uint64_t(rate*2));assert(e->localSource->readOperations()<=8);e->running=true;e->playLocal(true);std::array<float,512> out{};
 for(int block=0;block<20;block++){e->playLocal(false);assert(e->localSource->prime(e->localCursor));e->playLocal(true);callback(*e,out.data(),256);for(int i=0;i<256;i++){uint64_t index=uint64_t(block)*256+i;assert(e->recordBuffer[i*2]==sample(index,0)/32768.f);assert(e->recordBuffer[i*2+1]==sample(index,channels==2?1:0)/32768.f);}}
 assert(e->localMissingFrames==0);auto cursor=e->localCursor.load();e->playLocal(false);callback(*e,out.data(),256);assert(e->localCursor==cursor);
 e->playLocal(true);e->seekLocal(.1);e->seekLocal(.5);e->seekLocal(1.25);assert(e->localSeek.peek()==1.25);callback(*e,out.data(),256);assert(e->localCursor==uint64_t(1.25*rate)+256);
 e->playLocal(false);e->playLocal(true);auto next=e->localCursor.load();callback(*e,out.data(),256);assert(e->recordBuffer[0]==sample(next,0)/32768.f);
 e->playLocal(false);e->seekLocal(1.999);e->playLocal(true);for(int i=0;i<3;i++)callback(*e,out.data(),256);assert(!e->localPlaying);e->playLocal(true);assert(e->localCursor==0);callback(*e,out.data(),256);assert(e->recordBuffer[0]==sample(0,0)/32768.f);
 auto prior=e->localSource.get();assert(!e->loadLocalFile(dir+"/missing.pcm",rate,channels));assert(e->localSource.get()==prior);
 std::string replacement=dir+"/replace.pcm";pcm(replacement,8192,channels);assert(e->loadLocalFile(replacement,rate,channels));assert(e->localCursor==0&&e->localSource->frames()==8192&&!e->localPlaying);
 e->seekLocal(.02);double seconds=double(e->localCursor.load())/e->sr;e->stop(true);assert(e->localSource->frames()==8192);assert(std::abs(double(e->localCursor.load())/e->sr-seconds)<1e-8);e->running=false;
}
int main(int argc,char** argv){assert(argc==2);std::string dir=argv[1];for(int rate:{44100,48000})for(int ch:{1,2})run(dir,rate,ch);
 {
 std::string path=dir+"/twenty-minute.pcm";int f=open(path.c_str(),O_CREAT|O_TRUNC|O_WRONLY,0600);assert(f>=0);uint64_t frames=48000ull*60*20;assert(ftruncate(f,off_t(frames*4))==0);close(f);
 auto source=std::make_unique<LocalPcmSource>();assert(source->openPcm16(path.c_str(),48000,2));assert(source->frames()==frames&&source->readOperations()==8);assert(source->cacheBytes()==262144&&sizeof(LocalPcmSource)<300000);
 source->seek(frames-8192);assert(source->prime(frames-8192));float l,r;assert(source->sample(double(frames-8192),l,r)&&l==0&&r==0);source->close();assert(source->frames()==0);
 }
 {
 auto e=setup(48000);std::string path=dir+"/force-miss.pcm";pcm(path,100000,2);assert(e->loadLocalFile(path,48000,2));e->running=true;e->playLocal(true);e->localSource->pauseReadAhead();e->localSource->seek(90000);e->localCursor=90000;e->recording=true;
 std::array<float,8192> out{};callback(*e,out.data(),4096);assert(e->localCursor==94096);assert(e->localMissingFrames==4096&&e->localRecordingMissingFrames==4096);assert(e->localSource->underruns()==1);for(float v:out)assert(v==0);e->recording=false;e->running=false;
 }
 {
 auto e=setup(48000);std::string path=dir+"/slow.pcm";pcm(path,100000,2);assert(e->loadLocalFile(path,48000,2));auto old=e->localSource.get();blockRead=true;readEntered=false;releaseRead=false;
 auto start=std::chrono::steady_clock::now();assert(!e->loadLocalFile(path,48000,2));assert(std::chrono::steady_clock::now()-start<std::chrono::seconds(3));assert(readEntered&&e->localSource.get()==old&&e->retiredLocalSource);
 start=std::chrono::steady_clock::now();assert(!e->loadLocalFile(path,48000,2));assert(std::chrono::steady_clock::now()-start<std::chrono::milliseconds(100));releaseRead=true;blockRead=false;
 while(!e->retiredLocalSource->readerStopped())std::this_thread::yield();assert(e->loadLocalFile(path,48000,2));
 e->playLocal(true);blockRead=true;readEntered=false;releaseRead=false;e->localSource->seek(90000);auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(1);while(!readEntered&&std::chrono::steady_clock::now()<deadline)std::this_thread::yield();assert(readEntered);
 start=std::chrono::steady_clock::now();e->playLocal(false);assert(std::chrono::steady_clock::now()-start<std::chrono::milliseconds(100));releaseRead=true;blockRead=false;
 }
 {
 auto e=setup(48000);std::string path=dir+"/concurrent-seek.pcm";pcm(path,100000,2);assert(e->loadLocalFile(path,48000,2));e->running=true;e->playLocal(true);
 std::atomic<bool> rendering{true};std::thread output([&]{std::array<float,128> out{};while(rendering.load()){callback(*e,out.data(),64);std::this_thread::sleep_for(std::chrono::microseconds(100));}});
 for(int i=0;i<100;i++){e->seekLocal(i%2?.1:1.5);std::this_thread::sleep_for(std::chrono::microseconds(200));}
 e->playLocal(false);
 for(int i=0;i<40;i++){double target=i%2?.75:.25;e->seekLocal(target);std::this_thread::sleep_for(std::chrono::milliseconds(1));assert(e->localCursor==uint64_t(target*48000));}
 rendering=false;output.join();e->playLocal(true);std::array<float,512> out{};callback(*e,out.data(),256);assert(e->recordBuffer[0]==sample(36000,0)/32768.f);e->running=false;
 }
 assert(callbackAllocations==0&&callbackFileOperations==0);
 std::cout<<"PASS: bounded256KiB cache/20min sparse, PCM16 stereo/mono44100/48000 exact playback, EOF/replay/latest seek/pause/replacement/suspend, missing-silence clock continuity and recording warning; blocked-read pause nonjoining, failedprime2s prior-source retention; concurrent active/paused seek preserves latest position, callback allocation/fileIO zero\n";
}
