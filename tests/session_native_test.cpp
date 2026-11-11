#include "../app/src/main/cpp/session_transport.h"
#include <cassert>
#include <cstdio>
#include <unistd.h>
#include <cstring>
using namespace lm;
static void run(SessionTransport& s,int rate,int n,bool capture=false){assert(s.begin(rate));for(int i=0;i<n;i++){float l,r;s.sample(l,r,capture);}s.end();}
static int64_t field(SessionTransport& s,int i){int64_t st[11];s.state(st);return st[i];}
static std::unique_ptr<SessionTransport::Plan> empty(uint64_t length){auto p=std::make_unique<SessionTransport::Plan>();p->duration=length;return p;}
static void wavFormats(){
 char path[]="/tmp/livevault-session-wave-XXXXXX";int fd=mkstemp(path);assert(fd>=0);close(fd);
 for(int format:{1,3})for(int channels:{1,2}){FILE* f=fopen(path,"wb");assert(f);auto u16=[&](unsigned n){fputc(n&255,f);fputc((n>>8)&255,f);};auto u32=[&](unsigned n){u16(n&65535);u16(n>>16);};int bits=format==1?16:32;unsigned bytes=480*channels*bits/8;fwrite("RIFF",1,4,f);u32(bytes+36);fwrite("WAVEfmt ",1,8,f);u32(16);u16(format);u16(channels);u32(44100);u32(44100*channels*bits/8);u16(channels*bits/8);u16(bits);fwrite("data",1,4,f);u32(bytes);for(int i=0;i<480*channels;i++){if(format==1)u16(8192);else{float v=.25f;fwrite(&v,4,1,f);}}fclose(f);LocalPcmSource source;assert(source.openWav(path)&&source.frames()==480&&source.sampleRate()==44100);float l,r;assert(source.sample(12.5,l,r));assert(l==.25f&&r==.25f);source.close();}
 FILE* f=fopen(path,"wb");fwrite("garbage",1,7,f);fclose(f);LocalPcmSource invalid;assert(!invalid.openWav(path));unlink(path);
}
int main(){wavFormats();
 SessionTimeline t;for(int i=0;i<44100*600;i++)t.advance(44100);assert(t.frame()==48000ull*600);for(int i=0;i<48000;i++)t.advance(48000);assert(t.frame()==48000ull*601);
 SessionTransport s;assert(s.prepare(empty(96000)));s.setEnabled(true);assert(s.transport(1));run(s,44100,22050);assert(field(s,2)==24000);assert(s.transport(2));run(s,48000,48000);assert(field(s,2)==24000);assert(s.seek(12000));assert(s.transport(1));run(s,48000,24000);assert(field(s,2)==36000);assert(!s.seek(0));assert(s.transport(0));assert(field(s,2)==0);
 assert(s.transport(1));assert(s.armCapture(99));run(s,44100,4410,true);assert(field(s,8)==0&&field(s,9)==4800);assert(s.transport(2));run(s,48000,4800,true);assert(field(s,9)==4800);s.finishCapture();assert(s.seek(95000));assert(s.transport(1));run(s,48000,2000);assert(field(s,2)==96000&&field(s,1)==3);assert(s.transport(0));assert(s.prepare(empty(0)));assert(s.transport(1));run(s,48000,96001);assert(field(s,2)==96001&&field(s,1)==1);assert(s.transport(0));
 // Memory sources exercise overlapping baked clips directly on the fixed bed.
 auto p=empty(48000);for(int j=0;j<2;j++){SessionTransport::Clip c;c.track=j;c.start=0;c.length=48000;c.source=std::make_unique<LocalPcmSource>();c.source->loadMemory(std::vector<float>(96000,.2f));p->clips.push_back(std::move(c));}assert(s.prepare(std::move(p)));assert(s.transport(1));assert(s.begin(48000));float l,r;s.sample(l,r,false);s.end();assert(std::abs(l-.4f)<1e-6&&std::abs(r-.4f)<1e-6);assert(s.transport(2));auto bad=empty(10);SessionTransport::Clip c;c.track=9;bad->clips.push_back(std::move(c));auto rev=field(s,5);assert(!s.prepare(std::move(bad))&&field(s,5)==rev);assert(s.transport(0));
 puts("PASS session exact 44.1/48k clocks, rate changes, pause/seek/stop/EOF, capture anchor, overlap, rejected publication retains plan");
}
