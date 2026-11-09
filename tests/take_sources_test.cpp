#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <fstream>
#include <iostream>
#include <memory>
#include <thread>
#include <limits>
static std::vector<char> bytes(const std::string& file){std::ifstream f(file,std::ios::binary);return {std::istreambuf_iterator<char>(f),{}};}
static void sourceCase(const std::string& dir,int sync,bool paused,bool micOnly,bool harmony){
 auto e=std::make_unique<Engine>();e->sr=e->inputRate=48000;e->dsp.init(48000);e->feedback.init(48000,false);e->composer.sampleRate(48000);
 float params[]={1,1,.4f,2,.2f,100,.4f,1,-2,2,1,0,0,0,0,78,0,0,0,0};
 for(int i=0;i<20;i++)e->params[i]=params[i];e->recordSyncMs=sync;e->recordMicOnly=micOnly;
 e->harmony.configure(harmony,0,0,1,.5f,1,1);
 std::string stem=dir+"/source-"+std::to_string(sync)+std::to_string(paused)+std::to_string(micOnly)+std::to_string(harmony);
 AAudioStream input;e->publishedInput=&input;e->running=true;
 assert(e->beginRecord(stem+".wav",stem+".raw.wav",stem+".fixed.wav"));
 std::array<float,256> raw{};std::array<float,512> out{},bg{};e->playback.configure(true,1);
 for(int block=0;block<40;block++){
  if(paused&&block==10)e->pauseRecord(true);if(paused&&block==20)e->pauseRecord(false);
  for(int i=0;i<256;i++){raw[i]=.08f*std::sin((block*256+i)*.1f);bg[i*2]=.2f;bg[i*2+1]=-.1f;}
  input.samples=raw.data();input.frames=256;e->playback.push(bg.data(),512);e->render(out.data(),256);
 }
 e->stopRecord();e->running=false;assert(!e->sourceError);assert(e->sourceEditable==(!harmony||micOnly));
 SourceReader mic,fixed;assert(mic.open(stem+".raw.wav",1));assert(fixed.open(stem+".fixed.wav",2));assert(mic.frames==(paused?30:40)*256);assert(fixed.frames==mic.frames);
 for(uint32_t i=0;i<mic.frames;i++){float sample,l,r;assert(fread(&sample,4,1,mic.f)==1);assert(fread(&l,4,1,fixed.f)==1);assert(fread(&r,4,1,fixed.f)==1);assert(l==(micOnly?0:.2f));assert(r==(micOnly?0:-.1f));}
 auto original=bytes(stem+".wav"),source=bytes(stem+".raw.wav");std::atomic<bool> cancel{false};
 assert(renderSourceTake(stem+".raw.wav",stem+".fixed.wav",stem+".edit.wav",params,sync,cancel));
 assert(bytes(stem+".edit.wav").size()==original.size());
 if(!paused&&!harmony)assert(bytes(stem+".edit.wav")==original);
 params[2]=0;params[4]=0;assert(renderSourceTake(stem+".raw.wav",stem+".fixed.wav",stem+".dry.wav",params,sync,cancel));assert(bytes(stem+".dry.wav")!=bytes(stem+".edit.wav"));
 assert(bytes(stem+".wav")==original&&bytes(stem+".raw.wav")==source);
 cancel=true;assert(!renderSourceTake(stem+".raw.wav",stem+".fixed.wav",stem+".cancel.wav",params,sync,cancel));assert(bytes(stem+".cancel.wav").empty());
}
int main(int argc,char**argv){assert(argc==2);std::string dir=argv[1];
 for(int sync:{-250,0,250})sourceCase(dir,sync,false,false,false);
 sourceCase(dir,125,true,false,false);sourceCase(dir,0,false,true,true);sourceCase(dir,0,false,false,true);
 {
 auto e=std::make_unique<Engine>();e->running=true;e->dsp.init(48000);e->feedback.init(48000,false);
 std::string mix=dir+"/retained.wav";assert(e->beginRecord(mix,"/no-such-take-directory/raw.wav","/no-such-take-directory/fixed.wav"));
 std::array<float,512> out{};e->render(out.data(),256);e->stopRecord();e->running=false;assert(e->sourceError);assert(bytes(mix).size()>44);
 }
 {
 std::string raw=dir+"/source-0000.raw.wav",fixed=dir+"/source-0000.fixed.wav",bad=dir+"/corrupt.raw.wav",target=dir+"/bad-output.wav";
 auto source=bytes(raw);assert(source.size()>44);std::atomic<bool> cancel{false};float p[]={1,1,0,.3f,0,100,0,0,0,0,0,0,0,0,0,78,0,0,0,0};
 auto write=[&](std::vector<char> data){std::ofstream out(bad,std::ios::binary);out.write(data.data(),data.size());};
 auto malformed=source;malformed.pop_back();write(malformed);assert(!renderSourceTake(bad,fixed,target,p,0,cancel));
 malformed=source;float nan=std::numeric_limits<float>::quiet_NaN();memcpy(malformed.data()+44,&nan,4);write(malformed);assert(!renderSourceTake(bad,fixed,target,p,0,cancel));assert(bytes(target).empty());
 malformed=source;malformed[32]=1;write(malformed);assert(!renderSourceTake(bad,fixed,target,p,0,cancel));
 malformed=source;uint32_t invalidRate=INT32_MAX;memcpy(malformed.data()+24,&invalidRate,4);write(malformed);assert(!renderSourceTake(bad,fixed,target,p,0,cancel));
 malformed=source;malformed[4]^=1;write(malformed);assert(!renderSourceTake(bad,fixed,target,p,0,cancel));
 assert(!sourceOutputFits(UINT32_MAX,48000,500));assert(!sourceOutputFits(UINT64_MAX,48000,500));assert(!sourceOutputFits(100,INT32_MAX,0));assert(sourceStorageFits((uint64_t(UINT32_MAX)-36)/8));assert(!sourceStorageFits((uint64_t(UINT32_MAX)-36)/8+1));
 assert(!renderSourceTake(raw,fixed,raw,p,0,cancel));assert(bytes(raw)==source);
 }
 {
 auto e=std::make_unique<Engine>();e->running=true;e->dsp.init(48000);e->feedback.init(48000,false);
 std::string mix=dir+"/disk-retained.wav";assert(e->beginRecord(mix,"/dev/full",dir+"/disk.fixed.wav"));std::array<float,512> out{};e->render(out.data(),256);e->stopRecord();e->running=false;assert(e->sourceError);assert(bytes(mix).size()>44);
 }
 {
 RecentVoice recent;assert(recent.seconds()==-1);int rate;float samples[256];std::fill(samples,samples+256,.25f);recent.push(samples,256,48000);assert(recent.snapshot(rate).empty());recent.configure(true);
 for(int i=0;i<2100;i++)recent.push(samples,256,48000);
 auto copy=recent.snapshot(rate);assert(rate==48000&&copy.size()==480000);for(float value:copy)assert(value==.25f);
 assert(recent.seconds()==10);assert(!saveRecentVoiceFile(recent,"/no-such-history-directory/raw.wav"));assert(recent.seconds()==10);assert(recent.snapshot(rate).size()==480000);
 assert(saveRecentVoiceFile(recent,dir+"/recent.raw.wav"));assert(recent.seconds()==0);assert(recent.snapshot(rate).empty());
 recent.forget();assert(recent.snapshot(rate).empty());recent.configure(false);assert(recent.seconds()==-1);
 recent.configure(true);float block[192];std::fill(block,block+192,.5f);for(int i=0;i<2500;i++)recent.push(block,192,48000);
 std::atomic<bool> live{true},ready{false};std::thread liveProducer([&]{while(live){recent.push(block,192,48000);ready=true;std::this_thread::sleep_for(std::chrono::milliseconds(4));}});
 while(!ready)std::this_thread::yield();auto full=recent.snapshot(rate);assert(full.size()==480000);for(float value:full)assert(value==.5f);live=false;liveProducer.join();recent.forget();
 recent.configure(true);std::atomic<bool> done{false};std::thread producer([&]{for(int i=0;i<300;i++)recent.push(samples,256,48000);done=true;});
 while(!done){auto snapshot=recent.snapshot(rate);for(float value:snapshot)assert(value==.25f);}
 producer.join();recent.configure(false);assert(recent.snapshot(rate).empty());
 }
 std::cout<<"PASS: float32 source recording, exact plain-FX rerender, +/-sync, pause frame counts, fixed backing, unsupported harmony, source failure original retention, cancellation and bounded opt-in recent voice\n";
}
