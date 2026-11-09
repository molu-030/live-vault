// Execute the production callback and WAV writer. Only hardware reads are stubbed.
#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <fstream>
#include <iostream>
#include <memory>
#include <vector>

enum class Source { None, Capture, Local, Band, Composer };
struct Result { std::vector<float> recording,monitor;std::vector<unsigned char> wav; };
static const char* name(Source s){switch(s){case Source::Capture:return "capture";case Source::Local:return "local-file PCM";case Source::Band:return "procedural backing";case Source::Composer:return "composer backing";default:return "microphone";}}
static float background(size_t frame,int rate,int channel,bool clipping){
    float value=channel==0?.965f*std::sin(2*lm::PI*173*double(frame)/rate):-.957f*std::cos(2*lm::PI*317*double(frame)/rate);
    if(clipping&&frame%997==0)value=channel==0?1.12f:-1.13f;
    return value;
}
static Result render(Source source,bool effects,bool microphone,int rate,const std::string& path,const char* soundfont,int sync=0,bool clipping=false,bool leakage=false){
    constexpr int block=256;
    const int blocks=(rate*2+block-1)/block,frames=blocks*block;
    auto e=std::make_unique<Engine>();e->sr=e->inputRate=rate;e->inputChannels=1;
    e->dsp.init(rate);e->feedback.init(rate,false);e->composer.sampleRate(rate);
    float p[]={1,1,0,.3f,0,90,.7f,0,0,0,0,0,0,0,0,90,0,0,0,0};
    if(effects){p[2]=1;p[3]=6;p[4]=1;p[7]=8;p[8]=-6;p[9]=5;p[10]=1;}
    p[13]=source==Source::Band?1:0;
    for(int i=0;i<20;i++)e->params[i]=p[i];
    e->harmony.configure(effects,0,0,1,1,1,1);
    e->frequency=220;e->pitchConfidence=1;e->pitchLevel=.1f;
    AAudioStream input;input.rate=rate;e->publishedInput=&input;
    e->recordSyncMs=sync;
    if(source==Source::Capture)e->playback.configure(true,1);
    if(source==Source::Local){
        e->localRate=rate;e->localPCM.resize(size_t(frames+1)*2);
        for(int i=0;i<=frames;i++)for(int c=0;c<2;c++)e->localPCM[i*2+c]=background(i,rate,c,clipping);
        e->localPlaying=true;
    }
    if(source==Source::Composer){
        assert(e->composer.load(soundfont));
        std::vector<lv::Event> events={{0,3,0,0,1,8,0,0,0},{0,2,0,0,0,24,0,-.7f,.8f},{0,2,1,0,0,0,0,.8f,.7f},{0,1,0,60,.8f,0,0,0,0},{0,1,1,67,.75f,0,0,0,0},{1,0,0,60,0,0,0,0,0},{1,0,1,67,0,0,0,0,0}};
        assert(e->composer.publish(events,4,4,120,0,0));e->composer.transport(1);
    }
    e->running=true;assert(e->beginRecord(path));
    Result result;result.recording.reserve(frames*2);result.monitor.reserve(frames*2);
    std::array<float,block> mic{},pitch{},voiceInput{};
    std::array<float,block*2> captured{},out{};
    for(int b=0;b<blocks;b++){
        for(int i=0;i<block;i++){
            int frame=b*block+i;
            mic[i]=microphone&&frame<rate/4?.015f*std::sin(2*lm::PI*220*double(frame)/rate):0;
            // Negative control: loudspeaker sound returning through the mic is
            // already part of the mic bus, so voice effects necessarily change it.
            int delayed=frame-rate*137/1000;
            if(leakage&&delayed>=0)mic[i]=.08f*(background(delayed,rate,0,false)+background(delayed,rate,1,false));
            for(int c=0;c<2;c++)captured[i*2+c]=background(frame,rate,c,clipping);
        }
        input.samples=mic.data();input.frames=block;
        if(source==Source::Capture)assert(e->playback.push(captured.data(),captured.size())==captured.size());
        assert(e->render(out.data(),block)==AAUDIO_CALLBACK_RESULT_CONTINUE);
        // Both clean analyzers must receive only raw microphone samples.
        assert(e->pitchRing.pop(pitch.data(),block)==block);
        assert(e->composer.microphone.pop(voiceInput.data(),block)==block);
        for(int i=0;i<block;i++)assert(pitch[i]==mic[i]&&voiceInput[i]==mic[i]);
        result.recording.insert(result.recording.end(),e->recordBuffer.begin(),e->recordBuffer.begin()+block*2);
        result.monitor.insert(result.monitor.end(),out.begin(),out.end());
        if(source==Source::Capture&&!microphone)for(float sample:out)assert(sample==0);
        if((source==Source::Capture||source==Source::Local)&&!microphone&&!leakage&&sync==0)
            for(int i=0;i<block*2;i++)assert(e->recordBuffer[i]==lm::limit(captured[i]));
    }
    e->stopRecord();assert(!e->overflow&&!e->diskError);
    e->running=false;
    std::ifstream file(path,std::ios::binary);assert(file);result.wav.assign(std::istreambuf_iterator<char>(file),{});
    assert(result.wav.size()==44+size_t(frames+std::abs(sync)*rate/1000)*4);
    assert(std::string(result.wav.begin(),result.wav.begin()+4)=="RIFF");
    return result;
}
int main(int argc,char** argv){
    assert(argc==3);std::string directory=argv[1];const char* soundfont=argv[2];int id=0;
    auto run=[&](Source source,bool effects,bool mic,int rate,int sync=0,bool clipping=false,bool leakage=false){return render(source,effects,mic,rate,directory+"/bus-"+std::to_string(id++)+".wav",soundfont,sync,clipping,leakage);};
    for(int rate:{44100,48000}){
        for(Source source:{Source::Capture,Source::Local,Source::Band,Source::Composer}){
            auto dry=run(source,false,false,rate),wet=run(source,true,false,rate);
            assert(dry.wav==wet.wav);assert(dry.recording==wet.recording);assert(dry.monitor==wet.monitor);
            bool audible=false,stereo=false;for(size_t i=0;i<dry.recording.size();i+=2){audible|=std::abs(dry.recording[i])>.01f;stereo|=dry.recording[i]!=dry.recording[i+1];}assert(audible);if(source!=Source::Band)assert(stereo);
            std::cout<<"PASS: "<<rate<<"Hz "<<name(source)<<" extreme FX on/off recorded WAV byte-identical; clean analyzers isolated\n";
        }
        auto dryVoice=run(Source::None,false,true,rate),wetVoice=run(Source::None,true,true,rate);
        assert(dryVoice.wav!=wetVoice.wav);double wetTail=0,dryTail=0;
        for(size_t i=rate;i<wetVoice.recording.size();i++){wetTail+=wetVoice.recording[i]*wetVoice.recording[i];dryTail+=dryVoice.recording[i]*dryVoice.recording[i];}
        assert(wetTail>.00001&&wetTail>dryTail*100);
        for(Source source:{Source::Capture,Source::Local}){
            auto dryClipped=run(source,false,false,rate,0,true),wetClipped=run(source,true,false,rate,0,true);assert(dryClipped.wav==wetClipped.wav);
            auto mixed=run(source,true,true,rate,0,true);bool hitLimit=false;
            for(size_t i=0;i<mixed.recording.size();i++){
                assert(std::abs(wetVoice.recording[i])<.9f);
                float expected=lm::limit(wetVoice.recording[i]+background(i/2,rate,i%2,true));
                assert(mixed.recording[i]==expected);hitLimit|=std::abs(expected)==.98f;
            }assert(hitLimit);
        }
        std::cout<<"PASS: "<<rate<<"Hz voice-only FX tail changes; capture/local stereo sum uses processed voice + clean backing before final clipping\n";
        for(int sync:{-250,250}){auto a=run(Source::Capture,false,false,rate,sync),b=run(Source::Capture,true,false,rate,sync);assert(a.wav==b.wav);}
        std::cout<<"PASS: "<<rate<<"Hz +/-250ms recording alignment leaves digital capture FX-independent\n";
        auto leakDry=run(Source::Capture,false,false,rate,0,false,true),leakWet=run(Source::Capture,true,false,rate,0,false,true);
        assert(leakDry.wav!=leakWet.wav);
        std::cout<<"PASS: "<<rate<<"Hz negative control: delayed backing entering mic changes with voice FX, while digital capture stays on clean bus\n";
    }
}
