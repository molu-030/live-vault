#pragma once
#include <atomic>
#include <memory>
#include <vector>
#include <algorithm>
#include <thread>
#include <mutex>
#include "take_sources.h"
// Atomic cells make worker snapshots data-race-free while the callback overwrites old samples.
class RecentVoice {
 static constexpr size_t Max=1920000;
 std::unique_ptr<std::atomic<float>[]> samples{new std::atomic<float>[Max]};
 std::atomic<uint64_t> version{0},written{0};
 std::atomic<int> rate{0};
 std::atomic<bool> enabled{false};
 std::atomic<int> writers{0};
 std::atomic<uint64_t> epoch{0};
 std::atomic<bool> snapshotting{false};
 std::mutex control;
public:
 RecentVoice(){clear();}
 bool isEnabled()const{return enabled.load();}
 void configure(bool on){std::lock_guard<std::mutex> lock(control);snapshotting=true;enabled=false;while(writers.load())std::this_thread::yield();clear();enabled=on;snapshotting=false;}
 void forget(){std::lock_guard<std::mutex> lock(control);snapshotting=true;bool on=enabled.exchange(false);while(writers.load())std::this_thread::yield();clear();enabled=on;snapshotting=false;}
 int seconds()const{if(!enabled.load())return -1;int sr=rate.load();return sr>0?int(std::min<uint64_t>(written.load(),std::min<uint64_t>(Max,uint64_t(sr)*10))/sr):0;}
 uint64_t generation()const{return epoch.load();}
 void clear(){epoch++;version.fetch_add(1);for(size_t i=0;i<Max;i++)samples[i]=0;written=0;rate=0;version.fetch_add(1);}
 void push(const float* data,size_t count,int sampleRate){
  if(sampleRate<8000||sampleRate>192000||!enabled.load()||snapshotting.load())return;
  writers++;if(!enabled.load()||snapshotting.load()){writers--;return;}
  version.fetch_add(1,std::memory_order_acq_rel);
  if(rate!=sampleRate){written=0;rate=sampleRate;}
  uint64_t start=written.load();size_t capacity=std::min(Max,size_t(sampleRate)*10);
  for(size_t i=0;i<count;i++)samples[(start+i)%capacity].store(data[i],std::memory_order_relaxed);
  written=start+count;version.fetch_add(1,std::memory_order_release);writers--;
 }
 bool commitSnapshot(uint64_t generation){
  std::lock_guard<std::mutex> lock(control);
  if(epoch.load()!=generation||!enabled.load())return false;
  snapshotting=true;while(writers.load())std::this_thread::yield();clear();snapshotting=false;return true;
 }
 std::vector<float> snapshot(int& sampleRate,uint64_t* generation=nullptr){
  // Freeze history collection only. Audio callbacks skip history instead of
  // blocking, so even a full ten-second copy makes progress on slower devices.
  std::lock_guard<std::mutex> lock(control);snapshotting=true;
  while(writers.load())std::this_thread::yield();
  sampleRate=rate.load();
  if(!enabled.load()||sampleRate<=0){snapshotting=false;return {};}
  size_t capacity=std::min(Max,size_t(sampleRate)*10);uint64_t end=written.load();size_t count=std::min<uint64_t>(end,capacity);
  std::vector<float> copy;
  try{copy.resize(count);for(size_t i=0;i<count;i++)copy[i]=samples[(end-count+i)%capacity].load(std::memory_order_relaxed);}
  catch(...){snapshotting=false;return {};}
  if(generation)*generation=epoch.load();snapshotting=false;return copy;
 }
};

// Worker-only export. Failure leaves rolling history available for another attempt.
inline bool saveRecentVoiceFile(RecentVoice& recent,const std::string& path,bool reset=true,uint64_t* token=nullptr){
 uint64_t generation=0;int rate=0;auto samples=recent.snapshot(rate,&generation);if(samples.empty())return false;
 FILE* file=fopen(path.c_str(),"wbx");
 if(!file){std::fill(samples.begin(),samples.end(),0);return false;}
 lm::sourceHeader(file,rate,1,samples.size()*4);bool ok=true;
 for(size_t start=0;start<samples.size();start+=1024){
  size_t count=std::min<size_t>(1024,samples.size()-start);
  if(generation!=recent.generation()||!recent.isEnabled()||fwrite(samples.data()+start,4,count,file)!=count){ok=false;break;}
 }
 if(generation!=recent.generation()||!recent.isEnabled()||ferror(file))ok=false;
 std::fill(samples.begin(),samples.end(),0);if(fclose(file)!=0)ok=false;
 if(generation!=recent.generation()||!recent.isEnabled())ok=false;
 if(ok&&reset)ok=recent.commitSnapshot(generation);
 if(ok&&token)*token=generation;
 if(!ok)remove(path.c_str());return ok;
}
