#pragma once
#include "audio_core.h"
#include "record_alignment.h"
#include <cstdio>
#include <string>

namespace lm {
inline void source16(FILE* f,uint16_t v){unsigned char b[]={uint8_t(v),uint8_t(v>>8)};fwrite(b,1,2,f);}
inline void source32(FILE* f,uint32_t v){unsigned char b[]={uint8_t(v),uint8_t(v>>8),uint8_t(v>>16),uint8_t(v>>24)};fwrite(b,1,4,f);}
inline void sourceHeader(FILE* f,int rate,int channels,uint32_t bytes,bool floating=true){
 fseek(f,0,SEEK_SET);fwrite("RIFF",1,4,f);source32(f,bytes+36);fwrite("WAVEfmt ",1,8,f);source32(f,16);source16(f,floating?3:1);source16(f,channels);source32(f,rate);source32(f,rate*channels*(floating?4:2));source16(f,channels*(floating?4:2));source16(f,floating?32:16);fwrite("data",1,4,f);source32(f,bytes);
}
inline bool sourceStorageFits(uint64_t frames){return frames<=(uint64_t(UINT32_MAX)-36)/8;}
inline bool sourceOutputFits(uint64_t frames,int rate,int sync){
 if(rate<8000||rate>192000||sync < -500||sync > 500)return false;
 uint64_t tail=uint64_t(std::abs(sync))*uint64_t(rate)/1000;
 uint64_t maximum=(uint64_t(UINT32_MAX)-36)/4;
 return frames<=maximum && tail<=maximum-frames;
}
struct SourceReader {
 FILE* f=nullptr;int rate=0,channels=0;uint32_t frames=0;
 ~SourceReader(){if(f)fclose(f);}
 bool open(const std::string& path,int expectedChannels){
  if(expectedChannels!=1&&expectedChannels!=2)return false;
  f=fopen(path.c_str(),"rb");if(!f)return false;
  unsigned char h[44];if(fread(h,1,44,f)!=44)return false;
  auto u16=[&](int n){return int(h[n])|(int(h[n+1])<<8);};
  auto u32=[&](int n){return uint32_t(h[n])|(uint32_t(h[n+1])<<8)|(uint32_t(h[n+2])<<16)|(uint32_t(h[n+3])<<24);};
  uint32_t sampleRate=u32(24),bytes=u32(40);
  if(sampleRate<8000||sampleRate>192000||uint64_t(bytes)+36>UINT32_MAX||u32(4)!=bytes+36)return false;
  rate=int(sampleRate);channels=u16(22);frames=bytes/(4*expectedChannels);
  if(fseek(f,0,SEEK_END)!=0)return false;long length=ftell(f);if(fseek(f,44,SEEK_SET)!=0)return false;
  if(length<44||uint64_t(length-44)!=bytes||bytes%(4*expectedChannels)!=0||frames==0)return false;
  return !memcmp(h,"RIFF",4)&&!memcmp(h+8,"WAVEfmt ",8)&&!memcmp(h+36,"data",4)&&u16(20)==3&&u16(34)==32&&u32(16)==16&&u16(32)==4*expectedChannels&&u32(28)==sampleRate*4u*uint32_t(expectedChannels)&&channels==expectedChannels;
 }
};
inline bool renderSourceTake(const std::string& microphone,const std::string& backing,const std::string& output,const float* p,int sync,std::atomic<bool>& cancel){
 if(output==microphone||(!backing.empty()&&output==backing))return false;
 for(int i=0;i<20;i++)if(!std::isfinite(p[i]))return false;
 SourceReader mic,bg;if(!mic.open(microphone,1)||!sourceOutputFits(mic.frames,mic.rate,sync))return false;
 bool music=!backing.empty();if(music&&(!bg.open(backing,2)||bg.rate!=mic.rate||bg.frames!=mic.frames))return false;
 FILE* out=fopen(output.c_str(),"wbx");if(!out)return false;
 sourceHeader(out,mic.rate,2,0,false);DSP dsp;dsp.init(mic.rate);dsp.configure(p);RecordAlignment align;align.init(mic.rate,sync);
 uint32_t bytes=0;bool ok=true;uint64_t total=uint64_t(mic.frames)+align.tail();
 std::array<float,2048> raw{};std::array<float,4096> bed{};std::array<int16_t,4096> pcm{};
 for(uint64_t position=0;position<total;position+=2048){
  if(cancel.load()){ok=false;break;}
  size_t count=std::min<uint64_t>(2048,total-position),read=position<mic.frames?std::min<uint64_t>(count,mic.frames-position):0;
  if(read&&(fread(raw.data(),4,read,mic.f)!=read||(music&&fread(bed.data(),4,read*2,bg.f)!=read*2))){ok=false;break;}
  for(size_t i=0;i<count;i++){
   float vl=0,vr=0,ml=0,mr=0;
   if(i<read){if(music){ml=bed[i*2];mr=bed[i*2+1];}if(!std::isfinite(raw[i])||!std::isfinite(ml)||!std::isfinite(mr)){ok=false;break;}dsp.process(raw[i],p,vl,vr);}
   if(!std::isfinite(vl)||!std::isfinite(vr)){ok=false;break;}
   align.tick(vl,vr,ml,mr);pcm[i*2]=std::lround(limit(vl+ml)*32767);pcm[i*2+1]=std::lround(limit(vr+mr)*32767);
  }
  if(!ok||fwrite(pcm.data(),2,count*2,out)!=count*2){ok=false;break;}bytes+=count*4;
 }
 if(cancel.load())ok=false;if(ok)sourceHeader(out,mic.rate,2,bytes,false);if(ferror(out))ok=false;if(fclose(out)!=0)ok=false;if(!ok)remove(output.c_str());return ok;
}
// The stem already contains live DSP/harmony. Add only the requested delta
// against a neutral DSP baseline, preserving stereo and its existing processing.
// Direct gain is outside the delta so DC and very low frequencies gain correctly.
inline bool renderVocalStem(const std::string& vocal,const std::string& backing,const std::string& output,const float* p,int sync,std::atomic<bool>& cancel){
 if(output==vocal||(!backing.empty()&&output==backing))return false;
 for(int i=0;i<20;i++)if(!std::isfinite(p[i]))return false;
 SourceReader voice,bg;if(!voice.open(vocal,2)||!sourceOutputFits(voice.frames,voice.rate,sync))return false;
 bool music=!backing.empty();if(music&&(!bg.open(backing,2)||bg.rate!=voice.rate||bg.frames!=voice.frames))return false;
 bool neutral=p[2]==0&&p[4]==0&&p[7]==0&&p[8]==0&&p[9]==0&&p[10]<=.5f&&p[19]<=.5f;
 float fx[20],base[20];std::copy(p,p+20,fx);std::copy(p,p+20,base);fx[0]=base[0]=1;
 base[2]=base[4]=base[7]=base[8]=base[9]=base[10]=base[19]=0;
 DSP left,right,baselineL,baselineR;
 if(!neutral){for(DSP* d:{&left,&right,&baselineL,&baselineR})d->init(voice.rate);left.configure(fx);right.configure(fx);baselineL.configure(base);baselineR.configure(base);}
 RecordAlignment align;align.init(voice.rate,sync);
 FILE* out=fopen(output.c_str(),"wbx");if(!out)return false;sourceHeader(out,voice.rate,2,0,false);
 uint32_t bytes=0;bool ok=true;uint64_t total=uint64_t(voice.frames)+align.tail();
 std::array<float,4096> raw{},bed{};std::array<int16_t,4096> pcm{};
 for(uint64_t position=0;position<total;position+=2048){
  if(cancel.load()){ok=false;break;}
  size_t count=std::min<uint64_t>(2048,total-position),read=position<voice.frames?std::min<uint64_t>(count,voice.frames-position):0;
  if(read&&(fread(raw.data(),4,read*2,voice.f)!=read*2||(music&&fread(bed.data(),4,read*2,bg.f)!=read*2))){ok=false;break;}
  for(size_t i=0;i<count;i++){
   float vl=0,vr=0,ml=0,mr=0;
   if(i<read){
    vl=raw[i*2];vr=raw[i*2+1];if(music){ml=bed[i*2];mr=bed[i*2+1];}
    if(!std::isfinite(vl)||!std::isfinite(vr)||!std::isfinite(ml)||!std::isfinite(mr)){ok=false;break;}
    vl*=p[0];vr*=p[0];
    if(!neutral){float fl,fr,bl,br;left.process(vl,fx,fl,fr);baselineL.process(vl,base,bl,br);vl+=fl-bl;right.process(vr,fx,fl,fr);baselineR.process(vr,base,bl,br);vr+=fr-br;}
   }
   if(!std::isfinite(vl)||!std::isfinite(vr)){ok=false;break;}
   align.tick(vl,vr,ml,mr);pcm[i*2]=std::lround(limit(vl+ml)*32767);pcm[i*2+1]=std::lround(limit(vr+mr)*32767);
  }
  if(!ok||fwrite(pcm.data(),2,count*2,out)!=count*2){ok=false;break;}bytes+=count*4;
 }
 if(cancel.load())ok=false;if(ok)sourceHeader(out,voice.rate,2,bytes,false);if(ferror(out))ok=false;if(fclose(out)!=0)ok=false;if(!ok)remove(output.c_str());return ok;
}

}
