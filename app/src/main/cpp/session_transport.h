#pragma once
#include "session_timeline.h"
#include "local_pcm_source.h"
#include <memory>
#include <mutex>
#include <string>
namespace lm {
class SessionTransport {
public:
 struct Clip {int track=0;uint64_t start=0,offset=0,length=0,loopLength=0;bool loop=false;float gain=1,pan=0;std::unique_ptr<LocalPcmSource> source;};
 struct Plan {std::vector<Clip> clips;uint64_t duration=0;double bpm=90;};
private:
 std::unique_ptr<Plan> plan;std::mutex control;
 std::atomic<bool> blocked{false};std::atomic<int> readers{0};
 SessionTimeline timeline;
 std::atomic<uint64_t> frameSnapshot{0},durationSnapshot{0},revision{0},misses{0};
 std::atomic<int> mode{0},outputRate{48000};std::atomic<bool> enabled{false};
 std::atomic<int64_t> captureToken{0},captureAnchor{-1};std::atomic<uint64_t> captureTicks{0};
 bool callbackLease=false; // audio-thread only
 void block(){blocked=true;while(readers.load(std::memory_order_seq_cst))std::this_thread::yield();}
 void unblock(){blocked=false;}
 void idle(){if(plan)for(auto& c:plan->clips)c.source->idle();}
 bool primeAt(uint64_t f){if(!plan)return false;for(auto& c:plan->clips){uint64_t delta=f>c.start?f-c.start:0;if(delta>=c.length)continue;uint64_t sourceFrame=(c.offset+(c.loop?delta%c.loopLength:delta))*c.source->sampleRate()/48000;if(!c.source->primeRange(sourceFrame,std::min<uint64_t>(c.loop?(c.offset+c.loopLength)*c.source->sampleRate()/48000-sourceFrame:UINT64_MAX,4096ull*c.source->sampleRate()/44100+3))){idle();return false;}}return true;}
public:
 bool prepare(std::unique_ptr<Plan> next){
  if(!next||next->duration>48000ull*60*60*12||!std::isfinite(next->bpm)||next->bpm<20||next->bpm>400||next->clips.size()>32)return false;
  for(auto& c:next->clips){if(c.track<0||c.track>=8||!c.source||c.source->empty()||c.length==0||c.length>48000ull*60*60*12||c.start>48000ull*60*60*12-c.length||c.offset>48000ull*60*60*12||(next->duration&&(c.start>next->duration||c.length>next->duration-c.start))||!std::isfinite(c.gain)||c.gain<0||c.gain>4||!std::isfinite(c.pan)||std::abs(c.pan)>1)return false;
   if(c.loop&&(c.loopLength==0||c.loopLength>c.length))return false;uint64_t required=((c.loop?c.loopLength:c.length)*uint64_t(c.source->sampleRate())+47999)/48000;uint64_t sourceOffset=c.offset*uint64_t(c.source->sampleRate())/48000;if(sourceOffset>=c.source->frames()||required>c.source->frames()-sourceOffset)return false;
   if(c.loop)c.source->loopWindow(sourceOffset,sourceOffset+required);if(!c.source->primeRange(c.offset*uint64_t(c.source->sampleRate())/48000,std::min<uint64_t>(required,4096ull*c.source->sampleRate()/44100+3)))return false;c.source->idle();}
  std::lock_guard<std::mutex> lock(control);if(mode==1||captureToken!=0)return false;block();plan.swap(next);timeline.seek(plan->duration?std::min(frameSnapshot.load(),plan->duration):frameSnapshot.load());frameSnapshot=timeline.frame();durationSnapshot=plan->duration;revision++;mode=2;unblock();return true;
 }
 bool transport(int command){std::lock_guard<std::mutex> lock(control);if(command<0||command>2)return false;block();bool ok=true;
  if(command==1){if(!enabled||!plan)ok=false;else{if(plan->duration&&timeline.frame()>=plan->duration)timeline.seek(0);ok=primeAt(timeline.frame());if(ok)mode=1;}}
  else{mode=command==0?0:2;idle();if(command==0){timeline.seek(0);frameSnapshot=0;captureToken=0;}}
  unblock();return ok;
 }
 bool seek(uint64_t f){std::lock_guard<std::mutex> lock(control);if(mode==1||captureToken!=0||!plan||(plan->duration&&f>plan->duration))return false;block();uint64_t old=timeline.frame();bool ok=f==plan->duration||primeAt(f);idle();if(ok){timeline.seek(f);frameSnapshot=f;}else primeAt(old);idle();unblock();return ok;}
 void setEnabled(bool value){std::lock_guard<std::mutex> lock(control);block();enabled=value;if(!value){mode=0;timeline.seek(0);frameSnapshot=0;captureToken=0;idle();}unblock();}
 bool isEnabled()const{return enabled.load();}
 bool armCapture(int64_t token){if(token<=0||!enabled)return false;captureAnchor=-1;captureTicks=0;captureToken=token;return true;}
 void finishCapture(){std::lock_guard<std::mutex> lock(control);block();captureToken=0;unblock();}
 bool captureArmed()const{return captureToken.load()!=0;}
 // One bounded lease per callback. Publication and destruction are control-only.
 bool begin(int rate){readers.fetch_add(1,std::memory_order_seq_cst);if(blocked.load(std::memory_order_seq_cst)){readers.fetch_sub(1,std::memory_order_seq_cst);return false;}callbackLease=true;outputRate=rate;return enabled&&plan;}
 double beat()const{return plan?timeline.position()*plan->bpm/(48000.*60):0;}
 bool playing()const{return enabled&&mode==1;}
 void sample(float& l,float& r,bool capturing){l=r=0;if(!callbackLease||!enabled||!plan||mode!=1)return;
  uint64_t f=timeline.frame();if(plan->duration&&f>=plan->duration&&captureToken.load()==0){mode=3;idle();return;}
  if(capturing&&captureToken.load()!=0){int64_t empty=-1;captureAnchor.compare_exchange_strong(empty,int64_t(f));captureTicks.fetch_add(SessionTimeline::tickRate/uint64_t(outputRate));}
  double pos=timeline.position();for(auto& c:plan->clips){if(pos<c.start||pos>=c.start+c.length)continue;double relative=pos-c.start;if(c.loop)relative=std::fmod(relative,double(c.loopLength));double at=(c.offset+relative)*c.source->sampleRate()/48000.;c.source->prefetch(uint64_t(at));float a=0,b=0;if(c.source->sample(at,a,b)){l+=a*c.gain*(c.pan>0?1-c.pan:1);r+=b*c.gain*(c.pan<0?1+c.pan:1);}else misses++;}
  timeline.advance(outputRate);if(plan->duration&&timeline.frame()>=plan->duration&&captureToken.load()==0){timeline.seek(plan->duration);mode=3;idle();}frameSnapshot=timeline.frame();
 }
 void end(){if(callbackLease){callbackLease=false;readers.fetch_sub(1,std::memory_order_seq_cst);}}
 void state(int64_t* s)const{s[0]=enabled;s[1]=mode;s[2]=frameSnapshot;s[3]=durationSnapshot;s[4]=48000;s[5]=revision;s[6]=misses;s[7]=captureToken;s[8]=captureAnchor;s[9]=captureTicks/SessionTimeline::ticksPerFrame;s[10]=outputRate;}
};
}
