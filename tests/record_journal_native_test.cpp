#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <fstream>
#include <iostream>
#include <memory>
#include <sys/stat.h>
#include <sys/wait.h>
static std::string textFile(const std::string& p){std::ifstream f(p);return {std::istreambuf_iterator<char>(f),{}};}
static uint64_t watermark(const std::string& text,const std::string& name){auto begin=text.find("\""+name+"\":{");assert(begin!=std::string::npos);auto frames=text.find("\"frames\":",begin);assert(frames!=std::string::npos);return std::stoull(text.substr(frames+9));}
static uint64_t length(const std::string& p){struct stat st{};assert(stat(p.c_str(),&st)==0);return st.st_size;}
static std::unique_ptr<Engine> setup(){auto e=std::make_unique<Engine>();e->dsp.init(48000);e->feedback.init(48000,false);e->composer.sampleRate(48000);e->running=true;return e;}
int main(int argc,char** argv){assert(argc==2);std::string dir=argv[1],path=dir+"/journal-complete.wav";
 {auto e=setup();assert(e->beginRecord(path,path+".raw",path+".fixed",path+".vocal"));std::array<float,512> out{};for(int i=0;i<40;i++)e->render(out.data(),256);e->stopRecord();e->running=false;assert(!e->diskError&&!e->sourceError&&!e->vocalStemError);std::string journal=textFile(path+".journal");assert(journal.find("\"done\":true")!=std::string::npos);for(auto suffix:{std::string(""),std::string(".raw"),std::string(".fixed"),std::string(".vocal")}){std::string key=suffix.empty()?"mix":suffix.substr(1);assert(watermark(journal,key)==10240);int bytes=key=="mix"?4:key=="raw"?4:8;assert(length(path+suffix)==44+10240*bytes);}}
 {std::string mix=dir+"/journal-crash.wav";pid_t child=fork();assert(child>=0);if(child==0){auto e=setup();assert(e->beginRecord(mix,mix+".raw",mix+".fixed",mix+".vocal"));std::array<float,512> out{};for(int i=0;i<40;i++)e->render(out.data(),256);e->pauseRecord(true);auto deadline=std::chrono::steady_clock::now()+std::chrono::seconds(4);while(std::chrono::steady_clock::now()<deadline){auto journal=textFile(mix+".journal");if(journal.find("\"frames\":10240")!=std::string::npos)_exit(0);std::this_thread::sleep_for(std::chrono::milliseconds(10));}_exit(9);}int status=0;assert(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0);std::string journal=textFile(mix+".journal");assert(journal.find("\"done\":false")!=std::string::npos);for(auto name:{"mix","raw","fixed","vocal"})assert(watermark(journal,name)==10240);assert(length(mix)==44+10240*4);std::ifstream f(mix,std::ios::binary);f.seekg(40);uint32_t header=999;f.read(reinterpret_cast<char*>(&header),4);assert(header==0);}
 {auto e=setup();e->alignment.init(48000,20);e->recording=true;e->writerRunning=false;std::vector<float> full(1048576,0);assert(e->recordRing.push(full.data(),full.size())==full.size());auto begin=std::chrono::steady_clock::now();e->stopRecord();assert(std::chrono::steady_clock::now()-begin<std::chrono::seconds(1));assert(e->overflow&&e->writerDroppedFrames==960);e->running=false;}
 std::cout<<"PASS: production writer independent durable journal watermarks, synced final headers, abrupt exit with stale WAV header and recoverable committed payload, bounded tail admission after writer exit\n";
}
