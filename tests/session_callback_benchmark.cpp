// Optional diagnostic, not a hardware/latency performance test.
#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <cstdio>
#include <unistd.h>
static double measure(Engine& e){float out[512];for(int i=0;i<32;i++)e.render(out,256);auto start=std::chrono::steady_clock::now();for(int i=0;i<400;i++)e.render(out,256);return std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-start).count();}
int main(int argc,char**argv){assert(argc==2);auto e=std::make_unique<Engine>();e->dsp.init(48000);e->feedback.init(48000,false);assert(e->composer.load(argv[1]));std::vector<lv::Event> events;for(int ch=0;ch<8;ch++){events.push_back({0,2,ch,0,0,0,0,0,1});events.push_back({0,1,ch,60+ch,.5f,0,0,0,0});}assert(e->composer.publish(events,32,4,90,0,0));e->running=true;e->composer.transport(1);double legacy=measure(*e);e->composer.transport(2);double legacyPaused=measure(*e);
 char path[]="/tmp/livevault-session-bench-XXXXXX";int fd=mkstemp(path);assert(fd>=0);FILE* f=fdopen(fd,"wb");std::vector<float> pcm(8192,.005f);sourceHeader(f,48000,2,pcm.size()*4);fwrite(pcm.data(),4,pcm.size(),f);fclose(f);
 auto p=std::make_unique<SessionTransport::Plan>();p->duration=480000;for(int i=0;i<8;i++){SessionTransport::Clip c;c.track=i;c.length=480000;c.loop=true;c.loopLength=4096;c.source=std::make_unique<LocalPcmSource>();assert(c.source->openWav(path));p->clips.push_back(std::move(c));}assert(e->session.prepare(std::move(p)));e->session.setEnabled(true);assert(e->session.transport(1));double session=measure(*e);assert(e->session.transport(2));double sessionPaused=measure(*e);e->running=false;unlink(path);
 printf("DIAGNOSTIC 400 x256-frame host callbacks: legacy8-voice composer %.2fms; session8cachedclips+samecomposer %.2fms (%.2fx); paused legacy %.2fms/session %.2fms (%.2fx). No hardware performance claim.\n",legacy,session,session/legacy,legacyPaused,sessionPaused,sessionPaused/legacyPaused);
}
