#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <mutex>
#include <semaphore.h>
#include <cerrno>
#include <sys/stat.h>
#include <thread>
#include <vector>
namespace lm {
// Immutable memory compatibility source or bounded PCM16 read-ahead pages.
// File operations belong to the reader/control threads. Callback leases and
// pause/EOF are bounded atomics; no callback allocation, I/O, join or mutex.
class LocalPcmSource {
public:
 static constexpr size_t pageFrames=4096,pageCount=8;
private:
 struct Page {
  std::atomic<int> state{0}; // 0 empty, 1 writer, 2 ready, >=3 shared readers.
  std::atomic<uint64_t> generation{0},start{0};std::atomic<size_t> count{0};
  std::array<float,pageFrames*2> pcm{};
 };
 std::array<Page,pageCount> pages;
 std::vector<float> memory;
 FILE* file=nullptr;int channels=0,bytesPerSample=2,format=1;uint64_t dataOffset=0,frameCount=0;int sourceRate=48000;
 std::thread reader;std::atomic<bool> active{false},enabled{false},workerFinished{true};
 std::mutex wakeMutex;std::condition_variable wake;
 sem_t pageWake;std::atomic<bool> pageWakePending{false},cacheParked{false};std::atomic<uint64_t> pageWakeups{0};
 void signalPages(){if(!pageWakePending.exchange(true,std::memory_order_acq_rel))sem_post(&pageWake);}
 std::atomic<uint64_t> requested{0},generation{1},failures{0},underflows{0},reads{0};
 std::atomic<uint64_t> loopFirstPage{0},loopEndPage{0}; // configured before reader starts consuming
 size_t hint=0; // Only callback, or a control caller after callback quiescence.
 bool copyFrame(uint64_t index,float& l,float& r){
  uint64_t gen=generation.load(std::memory_order_acquire);
  for(size_t n=0;n<pageCount;n++){
   size_t slot=(hint+n)%pageCount;Page& p=pages[slot];int state=p.state.load(std::memory_order_acquire);bool leased=false;
   for(int attempt=0;attempt<2&&state>=2;attempt++)if(p.state.compare_exchange_strong(state,state+1,std::memory_order_acquire)){leased=true;break;}
   if(!leased)continue;
   bool found=p.generation.load()==gen&&index>=p.start.load()&&index-p.start.load()<p.count.load();
   if(found){size_t offset=size_t(index-p.start.load())*2;l=p.pcm[offset];r=p.pcm[offset+1];hint=slot;}
   p.state.fetch_sub(1,std::memory_order_release);if(found)return true;
  }
  return false;
 }
 void work(){
  std::array<unsigned char,pageFrames*2*4> input{};
  while(active.load(std::memory_order_acquire)){
   {std::unique_lock<std::mutex> lock(wakeMutex);wake.wait(lock,[this]{return !active.load()||enabled.load();});}
   if(!active.load())break;
   uint64_t gen=generation.load(std::memory_order_acquire),first=requested.load(std::memory_order_acquire)/pageFrames*pageFrames;bool progress=false,leasedDestination=false;
   std::array<uint64_t,pageCount> wanted{};for(size_t j=0;j<pageCount;j++){uint64_t at=first+j*pageFrames;if(loopEndPage>loopFirstPage&&at>=loopEndPage)at=loopFirstPage.load()+(at-loopEndPage.load())%(loopEndPage.load()-loopFirstPage.load());wanted[j]=at;}
   for(size_t ahead=0;ahead<pageCount&&active.load()&&enabled.load();ahead++){
    uint64_t start=wanted[ahead];if(start>=frameCount)break;bool present=false;
    for(Page& p:pages)if(p.state.load(std::memory_order_acquire)>=2&&p.generation.load()==gen&&p.start.load()==start){present=true;break;}
    if(present)continue;
    Page* destination=nullptr;
    for(Page& p:pages){int state=p.state.load(std::memory_order_acquire);if(state!=0&&state!=2)continue;
     bool wantedPage=false;for(auto at:wanted)if(p.start.load()==at)wantedPage=true;bool replace=state==0||p.generation.load()!=gen||!wantedPage;
     if(replace&&p.state.compare_exchange_strong(state,1,std::memory_order_acquire)){destination=&p;break;}
    }
    if(!destination){leasedDestination=true;continue;}Page& p=*destination;size_t count=size_t(std::min<uint64_t>(pageFrames,frameCount-start));
    bool ok=fseeko(file,off_t(dataOffset+start*channels*bytesPerSample),SEEK_SET)==0;
    size_t got=ok?fread(input.data(),size_t(channels)*bytesPerSample,count,file):0;reads.fetch_add(1,std::memory_order_relaxed);
    if(got!=count){failures.fetch_add(1,std::memory_order_relaxed);p.state.store(0,std::memory_order_release);active=false;break;}
    for(size_t i=0;i<got;i++)for(int c=0;c<2;c++){size_t at=(i*channels+(channels==2?c:0))*bytesPerSample;float value=0;if(format==3){std::memcpy(&value,input.data()+at,4);if(!std::isfinite(value))value=0;}else{int16_t v=int16_t(uint16_t(input[at])|(uint16_t(input[at+1])<<8));value=v/32768.f;}p.pcm[i*2+c]=value;}
    p.start=start;p.count=got;p.generation=gen;p.state.store(2,std::memory_order_release);progress=true;
    if(generation.load(std::memory_order_acquire)!=gen||requested.load()/pageFrames!=first/pageFrames)break;
   }
   if(!progress&&leasedDestination)std::this_thread::yield();
   else if(!progress&&active.load()&&enabled.load()){cacheParked=true;while(sem_wait(&pageWake)!=0&&errno==EINTR){}cacheParked=false;pageWakePending.store(false,std::memory_order_release);pageWakeups.fetch_add(1,std::memory_order_relaxed);}
  }
  workerFinished.store(true,std::memory_order_release);
 }
public:
 LocalPcmSource(){sem_init(&pageWake,0,0);}
 ~LocalPcmSource(){close();sem_destroy(&pageWake);}
 void idle(){if(enabled.exchange(false,std::memory_order_acq_rel))signalPages();}
 void pauseReadAhead(){idle();}
 bool readerStopped()const{return workerFinished.load(std::memory_order_acquire);}
 void requestClose(){
  {std::lock_guard<std::mutex> lock(wakeMutex);active=false;enabled=false;}
  wake.notify_one();signalPages();
 }
 // Final destruction is control/cleanup-only. Kernel-blocked filesystem reads
 // cannot be forcibly interrupted by portable C++; Engine retains such storage.
 void close(){requestClose();if(reader.joinable())reader.join();if(file)fclose(file);file=nullptr;memory.clear();frameCount=0;loopFirstPage=loopEndPage=0;for(Page& p:pages)p.state=0;hint=0;}
 bool openPcm16(const char* path,int rate,int ch){
  if(rate<8000||rate>192000||ch<1||ch>2)return false;
  FILE* next=fopen(path,"rb");if(!next)return false;
  struct stat info{};if(fstat(fileno(next),&info)!=0||!S_ISREG(info.st_mode)){fclose(next);return false;}
  bool ok=fseeko(next,0,SEEK_END)==0;off_t length=ok?ftello(next):-1;
  if(length<ch*4||uint64_t(length)>uint64_t(rate)*ch*2*20*60||length%(ch*2)){fclose(next);return false;}
  close();file=next;channels=ch;sourceRate=rate;format=1;bytesPerSample=2;dataOffset=0;frameCount=uint64_t(length)/(ch*2);requested=0;generation.fetch_add(1);failures=underflows=reads=0;active=true;enabled=true;workerFinished=false;
  reader=std::thread([this]{work();});
  auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(2);
  while(active.load()&&readyFrames()<std::min<uint64_t>(frameCount,pageFrames*pageCount)){
   if(std::chrono::steady_clock::now()>=deadline){failures.fetch_add(1);active=false;break;}
   std::this_thread::sleep_for(std::chrono::milliseconds(1));
  }
  bool loaded=failures.load()==0;idle();return loaded;
 }
 // RIFF WAVE only: validated PCM16 / IEEE float32, mono or stereo.
 bool openWav(const char* path){
  FILE* f=fopen(path,"rb");if(!f)return false;
  auto u16=[](const unsigned char* p){return unsigned(p[0])|(unsigned(p[1])<<8);};
  auto u32=[](const unsigned char* p){return uint32_t(p[0])|(uint32_t(p[1])<<8)|(uint32_t(p[2])<<16)|(uint32_t(p[3])<<24);};
  unsigned char h[16]{};bool ok=fread(h,1,12,f)==12&&!memcmp(h,"RIFF",4)&&!memcmp(h+8,"WAVE",4);
  int fmt=0,ch=0,rate=0,bits=0;uint64_t offset=0,bytes=0;
  struct stat st{};ok=ok&&fstat(fileno(f),&st)==0&&S_ISREG(st.st_mode);
  while(ok&&fread(h,1,8,f)==8){uint32_t size=u32(h+4);off_t start=ftello(f);if(start<0||uint64_t(start)+size>uint64_t(st.st_size)){ok=false;break;}
   if(!memcmp(h,"fmt ",4)){if(size<16||fread(h,1,16,f)!=16){ok=false;break;}fmt=u16(h);ch=u16(h+2);rate=int(u32(h+4));bits=u16(h+14);}
   else if(!memcmp(h,"data",4)){offset=uint64_t(start);bytes=size;}
   if(fseeko(f,start+off_t(size)+(size&1),SEEK_SET)!=0){ok=false;break;}
  }
  ok=ok&&(fmt==1&&bits==16||fmt==3&&bits==32)&&ch>=1&&ch<=2&&rate>=8000&&rate<=192000&&bytes>=uint64_t(ch*bits/8)*2&&bytes%(ch*bits/8)==0&&bytes<=uint64_t(rate)*ch*(bits/8)*20*60;
  if(!ok){fclose(f);return false;}close();file=f;channels=ch;sourceRate=rate;format=fmt;bytesPerSample=bits/8;dataOffset=offset;frameCount=bytes/(ch*bytesPerSample);requested=0;generation.fetch_add(1);failures=underflows=reads=0;active=true;enabled=false;workerFinished=false;reader=std::thread([this]{work();});bool loaded=prime(0);idle();return loaded;
 }
 void loopWindow(uint64_t first,uint64_t end){loopFirstPage=first/pageFrames*pageFrames;loopEndPage=(end+pageFrames-1)/pageFrames*pageFrames;}
 int sampleRate()const{return sourceRate;}
 void loadMemory(std::vector<float> samples){close();memory=std::move(samples);frameCount=memory.size()/2;requested=0;generation.fetch_add(1);failures=underflows=reads=0;}
 uint64_t frames()const{return frameCount;}
 bool empty()const{return frameCount<2;}
 void prefetch(uint64_t frame){uint64_t old=requested.exchange(frame,std::memory_order_acq_rel);if(old/pageFrames!=frame/pageFrames)signalPages();}
 void seek(uint64_t frame){requested.store(frame,std::memory_order_release);generation.fetch_add(1,std::memory_order_acq_rel);signalPages();}
 bool prime(uint64_t frame){
  if(!memory.empty())return true;if(!file||!active.load()||failures.load()!=0)return false;
  prefetch(frame);{std::lock_guard<std::mutex> lock(wakeMutex);enabled=true;}wake.notify_one();
  auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(2);
  while(active.load()&&std::chrono::steady_clock::now()<deadline){float l,r;if(copyFrame(frame,l,r)&&copyFrame(std::min(frame+1,frameCount-1),l,r))return true;std::this_thread::sleep_for(std::chrono::milliseconds(1));}
  idle();return false;
 }
 // Control-only priming for a complete worst-case session render block.
 bool primeRange(uint64_t first,uint64_t count){
  if(!prime(first))return false;if(!memory.empty())return true;
  uint64_t end=std::min(frameCount,first+count);auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(2);
  while(active.load()&&std::chrono::steady_clock::now()<deadline){bool ready=true;float l,r;for(uint64_t at=first;at<end;at=std::min(end,((at/pageFrames)+1)*pageFrames)){if(!copyFrame(at,l,r)){ready=false;break;}}if(ready&&(end<=first||copyFrame(end-1,l,r)))return true;std::this_thread::sleep_for(std::chrono::milliseconds(1));}idle();return false;
 }
 bool sample(double position,float& l,float& r){
  if(!std::isfinite(position)||position<0)return false;uint64_t index=uint64_t(position);if(index>=frameCount)return false;float fraction=float(position-index);
  if(!memory.empty()){size_t i=size_t(index)*2;l=memory[i]*(1-fraction)+memory[std::min(i+2,memory.size()-2)]*fraction;r=memory[i+1]*(1-fraction)+memory[std::min(i+3,memory.size()-1)]*fraction;return true;}
  float l0,r0,l1,r1;if(!copyFrame(index,l0,r0)||!copyFrame(std::min(index+1,frameCount-1),l1,r1))return false;l=l0*(1-fraction)+l1*fraction;r=r0*(1-fraction)+r1*fraction;return true;
 }
 void underrun(){underflows.fetch_add(1,std::memory_order_relaxed);}
 uint64_t underruns()const{return underflows.load();}uint64_t ioErrors()const{return failures.load();}
 bool parkedOnCache()const{return cacheParked.load(std::memory_order_acquire)&&!pageWakePending.load(std::memory_order_acquire);}
 uint64_t cacheWakeups()const{return pageWakeups.load();}
 uint64_t readOperations()const{return reads.load();}uint64_t seekGeneration()const{return generation.load();}
 uint64_t readyFrames(){uint64_t total=0,gen=generation.load();for(Page& p:pages)if(p.state.load(std::memory_order_acquire)>=2&&p.generation.load()==gen)total+=p.count.load();return total;}
 static constexpr size_t cacheBytes(){return pageFrames*pageCount*2*sizeof(float);}
};
}
