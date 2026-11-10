#pragma once
#include "voice_tracker.h"
#define TSF_IMPLEMENTATION
#include "vendor/tsf.h"
#include <mutex>
#include <string>
#include <thread>
#include <chrono>
namespace lv {
static constexpr int scales[5][7]={{0,2,4,5,7,9,11},{0,2,3,5,7,8,10},{0,2,3,5,7,8,11},{0,2,3,5,7,9,10},{0,2,4,5,7,9,10}};
static constexpr int intervals[11][5]={{0,4,7,-1},{0,3,7,-1},{0,3,6,-1},{0,4,8,-1},{0,2,7,-1},{0,5,7,-1},{0,4,7,10,-1},{0,4,7,11,-1},{0,3,7,10,-1},{0,3,6,10,-1},{0,4,7,14,-1}};
inline int pc(int n){return (n%12+12)%12;}
// Shared by the live trigger and host-side phrase fidelity checks. No allocation.
inline int guitarVoicing(int midi,int quality,int* tones){
    int open[]={40,45,50,55,59,64},bestScore=-999,best[6]{},bestN=0;
    for(int hand=0;hand<=8;hand++){
        int n=0,notes[6]{};
        for(int o:open)for(int fret=0;fret<=hand+4;fret++){
            if(fret>0&&fret<hand)continue;
            bool valid=false;
            for(int i=0;i<5&&intervals[quality][i]>=0;i++)if(pc(o+fret-midi)==pc(intervals[quality][i]))valid=true;
            if(valid){notes[n++]=o+fret;break;}
        }
        bool root=false,third=false;
        for(int j=0;j<n;j++){root|=pc(notes[j]-midi)==0;third|=pc(notes[j]-midi)==pc(intervals[quality][1]);}
        int score=n*20-hand+(n&&pc(notes[0])==pc(midi)?10:0)+(root?100:0)+(third?100:0);
        if(score>bestScore){bestScore=score;bestN=n;std::copy(notes,notes+n,best);}
    }
    std::copy(best,best+bestN,tones);return bestN;
}
struct Event {double beat;int type,ch,note;float velocity;int program,bank;float pan,gain;};
struct Plan {std::vector<Event> events;double beats=32,bar=4,bpm=90;int key=0,scale=0;std::atomic<int> status{0};};
struct Performance {double beat;int kind,midi,velocity;float cents,expression;int degree,quality;};
class ComposerEngine {
    tsf *backing=nullptr,*live=nullptr;std::atomic<bool> ready{false};Plan plans[3];int selected=-1;size_t cursor=0;std::mutex publishMutex,setupMutex;
    std::atomic<int> pending{-1},command{-1},voiceRevision{0};int appliedVoiceRevision=-1;
    double position=0,barLength=4,total=32,tempo=90;int rate=48000;uint64_t sample=0;
    std::atomic<float> gain[8],pan[8];std::atomic<bool> mute[8],solo[8],overrideTrack[8];float smoothGain[8]{},partGain[8]{},partPan[8]{};
    bool anySoloSnapshot=false;
    int currentRhythm=0,heldRoot=-1,heldVelocity=90;int currentVoice=-1,voiceNotes[6]{},voiceCount=0,octaveOffset=0,voiceDegree=-1,voiceQuality=0;bool anchored=false,deferRhythm=false;
    std::array<uint64_t,6> onFrame{};std::array<int,6> onNote{};std::array<float,6> onVelocity{};int pendingStrings=0;
    std::atomic<bool> voiceEnabled{false},voiceMonitor{true},autoRange{false},snap{false},rhythm{false};
    std::atomic<int> program{24},mode{0},octave{0},response{0},quality{-1};std::atomic<float> confidence{.85f},gate{.007f};
    float expression=1,cents=0;double lastRhythm=-1;std::atomic<int> recCommand{-1};double recStart=0,recEnd=0,recOrigin=0;int recordState=0;
    void silenceLive(){if(live){tsf_channel_sounds_off_all(live,0);tsf_channel_set_pitchwheel(live,0,8192);}currentVoice=heldRoot=-1;voiceCount=pendingStrings=0;}
    void silenceBacking(){if(backing)for(int ch=0;ch<8;ch++)tsf_channel_sounds_off_all(backing,ch);}
    void capture(int kind,int midi,int vel){int degree=voiceDegree;if(program/8==4){degree=-1;for(int d=0;d<7;d++)if(pc(midi-key.load())==scales[scale.load()][d])degree=d;}if(recordState==2&&!performance.push({position-recOrigin,kind,midi,vel,cents,expression,degree,voiceQuality}))overflow=true;}
    void release(){if(currentVoice>=0){capture(0,currentVoice,0);for(int i=0;i<voiceCount;i++)tsf_channel_note_off(live,0,voiceNotes[i]);}currentVoice=-1;voiceCount=pendingStrings=0;}
    void trigger(int midi,int velocity){
        release();currentVoice=midi;capture(1,midi,velocity);int tones[6]{},count=0;
        int family=program.load()/8;
        if(mode==0||family==4){tones[count++]=midi;}else if(family==3){count=guitarVoicing(midi,voiceQuality,tones);
        }else for(int i=0;i<5&&intervals[voiceQuality][i]>=0;i++)tones[count++]=std::clamp(midi+intervals[voiceQuality][i],0,127);
        pendingStrings=count;for(int i=0;i<count;i++){onNote[i]=tones[i];onVelocity[i]=velocity/127.f;onFrame[i]=sample+uint64_t((mode==1&&family==3?i*.012:0)*rate);}
    }
    int map(int midi){int original=midi;int k=key.load(),s=scale.load();if(snap||mode==1){int dist=99,best=midi;for(int n=midi-6;n<=midi+6;n++)for(int d=0;d<7;d++)if(pc(n-k)==scales[s][d]&&std::abs(n-midi)<dist){dist=std::abs(n-midi);best=n;}midi=best;}
        if(!anchored){octaveOffset=0;if(autoRange){int lo=program/8==4?28:program/8==3?40:48,hi=program/8==4?43:program/8==3?76:84;while(midi+octaveOffset<lo)octaveOffset+=12;while(midi+octaveOffset>hi)octaveOffset-=12;}anchored=true;}
        voiceDegree=-1;for(int d=0;d<7;d++)if(pc(midi-k)==scales[s][d])voiceDegree=d;
        voiceQuality=quality.load();if(voiceQuality<0&&voiceDegree>=0){int d=voiceDegree,third=scales[s][(d+2)%7]+(d+2)/7*12-scales[s][d],fifth=scales[s][(d+4)%7]+(d+4)/7*12-scales[s][d];voiceQuality=fifth==6?2:fifth==8?3:third==3?1:0;}if(voiceQuality<0)voiceQuality=0;
        inputNote=original;return std::clamp(midi+octaveOffset+octave*12,0,127);
    }
    void adopt(){int index=pending.exchange(-1,std::memory_order_acq_rel);if(index<0)return;int old=selected;selected=index;plans[index].status=3;Plan& p=plans[index];barLength=p.bar;total=p.beats;tempo=p.bpm;key=p.key;scale=p.scale;silenceBacking();if(position>=total)position=0;cursor=0;while(cursor<p.events.size()&&p.events[cursor].beat<position-.0001){if(p.events[cursor].type==2||p.events[cursor].type==3)dispatch(p.events[cursor]);cursor++;}if(old>=0)plans[old].status=0;}
    void dispatch(const Event& e){int ch=e.ch;if(e.type==3){part=e.note;currentRhythm=e.program;partStart=e.beat;partEnd=e.beat+e.velocity*barLength;}
        else if(e.type==2){tsf_channel_set_bank_preset(backing,ch,e.bank,e.program);partGain[ch]=e.gain;partPan[ch]=e.pan;overrideTrack[ch]=false;}
        else if(e.type==0)tsf_channel_note_off(backing,ch,e.note);
        else if(e.type==1)tsf_channel_note_on(backing,ch,e.note,e.velocity);
        else if(e.type==4){tsf_channel_set_pitchwheel(backing,ch,std::clamp(8192+int(e.note/200.f*8192),0,16383));tsf_channel_midi_control(backing,ch,11,std::clamp(int(e.velocity*127),0,127));}}
public:
    lm::Ring<32768> microphone;Queue<VoiceCommand,1024> voiceCommands;Queue<Performance,32768> performance;
    std::atomic<bool> playing{false},mixVoice{false},overflow{false};std::atomic<int> key{0},scale{0},part{0},inputNote{-1},outputNote{-1},recording{0};
    std::atomic<double> beat{0},partStart{0},partEnd{32};std::atomic<float> level{0};
    ComposerEngine(){for(int i=0;i<8;i++){gain[i]=1;pan[i]=0;mute[i]=solo[i]=overrideTrack[i]=false;}}
    ~ComposerEngine(){if(live)tsf_close(live);if(backing)tsf_close(backing);}
    bool load(const char* path){std::lock_guard<std::mutex> lock(setupMutex);if(ready)return true;backing=tsf_load_filename(path);if(!backing)return false;live=tsf_copy(backing);if(!live||!tsf_set_max_voices(backing,80)||!tsf_set_max_voices(live,16)){if(live)tsf_close(live);tsf_close(backing);live=backing=nullptr;return false;}for(int i=0;i<8;i++)tsf_channel_set_presetnumber(backing,i,0,0);tsf_channel_set_presetnumber(live,0,24,0);tsf_channel_set_pitchrange(live,0,2);tsf_set_output(backing,TSF_STEREO_INTERLEAVED,rate,-7);tsf_set_output(live,TSF_STEREO_INTERLEAVED,rate,-7);ready.store(true,std::memory_order_release);return true;}
    std::vector<std::string> kits(){std::lock_guard<std::mutex> lock(setupMutex);std::vector<std::string> names;if(backing)for(int i=0;i<backing->presetNum;i++)if(backing->presets[i].bank==128)names.push_back(std::to_string(backing->presets[i].preset)+" · "+backing->presets[i].presetName);return names;}
    void sampleRate(int sr){std::lock_guard<std::mutex> lock(setupMutex);rate=sr;sample=0;if(backing){tsf_set_output(backing,TSF_STEREO_INTERLEAVED,sr,-7);tsf_set_output(live,TSF_STEREO_INTERLEAVED,sr,-7);}}
    bool publish(const std::vector<Event>& data,double length,double bar,double bpm,int k,int s){std::lock_guard<std::mutex> lock(publishMutex);int slot=-1;for(int i=0;i<3;i++){int free=0;if(plans[i].status.compare_exchange_strong(free,1)){slot=i;break;}}if(slot<0)return false;Plan& p=plans[slot];p.events=data;p.beats=length;p.bar=bar;p.bpm=bpm;p.key=k;p.scale=s;p.status=2;int old=pending.exchange(slot,std::memory_order_acq_rel);if(old>=0)plans[old].status=0;return true;}
    void transport(int c){command=c;}
    void clearSolo(){for(auto& value:solo)value.store(false);}
    void track(int ch,float v,float p,bool m,bool s){if(ch>=0&&ch<8){gain[ch]=v;pan[ch]=p;mute[ch]=m;solo[ch]=s;overrideTrack[ch]=true;}}
    void settings(bool enabled,int pr,int md,int oct,bool range,bool sn,int rsp,int q,bool rh,bool mix,float conf,float gt){bool starting=enabled&&!voiceEnabled.load();voiceEnabled=enabled;program=pr;mode=md;octave=oct;autoRange=range;snap=sn;response=rsp;quality=q;rhythm=rh;mixVoice=mix;confidence=conf;gate=gt;if(starting)voiceMonitor=true;voiceRevision++;}
    void monitor(bool enabled){voiceMonitor=enabled;}
    void record(bool arm){recording=0;recCommand=arm?1:0;}
    bool enabled()const{return voiceEnabled;}
    bool monitoring()const{return voiceMonitor;}
    void analyze(const std::atomic<bool>& running){VoiceTracker tracker;std::array<float,2048> window{};std::array<float,480> hop{};int filled=0;bool was=false;int revision=-1;
        while(running){if(!voiceEnabled){if(was){VoiceCommand off;off.kind=0;voiceCommands.push(off);tracker.reset();filled=0;window.fill(0);level=0;}was=false;microphone.pop(hop.data(),hop.size());std::this_thread::sleep_for(std::chrono::milliseconds(2));continue;}
            if(revision!=voiceRevision){revision=voiceRevision;tracker.reset();filled=0;window.fill(0);level=0;}
            size_t count=std::min<size_t>(hop.size(),rate/100);if(microphone.available()<count){std::this_thread::sleep_for(std::chrono::milliseconds(1));continue;}microphone.pop(hop.data(),count);std::move(window.begin()+count,window.end(),window.begin());std::copy(hop.begin(),hop.begin()+count,window.end()-count);filled=std::min(2048,filled+int(count));was=true;if(filled<2048)continue;lm::Pitch p=lm::detectPitch(window.data(),2048,rate);level=p.rms;VoiceCommand c=tracker.process(p,response,confidence,gate);if(c.kind>=0&&!voiceCommands.push(c))overflow=true;
        }
    }
    void render(float* accompaniment,float* instrument,float* countIn,int count,bool external=false,double externalBeat=0,bool externalPlaying=false,bool externalBoundary=true){
        std::fill(accompaniment,accompaniment+count*2,0);std::fill(instrument,instrument+count*2,0);std::fill(countIn,countIn+count,0);if(!ready.load(std::memory_order_acquire))return;
        if(external){bool changed=playing.load()!=externalPlaying;if(playing&&!externalPlaying)silenceLive();if(externalBoundary)adopt();bool moved=externalBoundary&&std::abs(externalBeat-position)>tempo/(60.*rate)*1.5;if(changed||moved){silenceBacking();if(moved){cursor=0;if(selected>=0){auto& ev=plans[selected].events;while(cursor<ev.size()&&ev[cursor].beat<externalBeat){if(ev[cursor].type==2||ev[cursor].type==3)dispatch(ev[cursor]);cursor++;}}}}position=externalBeat;playing=externalPlaying;if(externalBoundary)command=-1;}
        if(!external||externalBoundary){
        int action=external?-1:command.exchange(-1);if(action>=0){if(action==0||action==2){playing=false;silenceBacking();if(recordState){recordState=3;recording=3;}if(action==0){position=0;cursor=0;}}else{if(!playing)adopt();if(action==3){position=-barLength;cursor=0;}playing=true;}}
        if(!playing)adopt();
        if(appliedVoiceRevision!=voiceRevision){appliedVoiceRevision=voiceRevision;silenceLive();anchored=false;tsf_channel_set_presetnumber(live,0,program,0);}
        VoiceCommand v;while(voiceCommands.pop(v)){if(!voiceEnabled)continue;if(v.kind==0){release();heldRoot=-1;inputNote=outputNote=-1;}else if(v.kind==1){int midi=map(v.midi);heldRoot=midi;heldVelocity=v.velocity;cents=mode==0?v.cents:0;expression=v.expression;if(mode==1&&rhythm){release();heldRoot=midi;deferRhythm=position>=0;}else trigger(midi,v.velocity);outputNote=midi;}else if(v.kind==2){expression=v.expression;cents=mode==0?v.cents:0;if(currentVoice>=0)capture(2,currentVoice,0);}}
        if(!voiceEnabled){silenceLive();inputNote=outputNote=-1;}
        int rc=recCommand.exchange(-1);if(rc==0){recordState=0;recording=0;}else if(rc==1){overflow=false;if(position<0){recStart=recOrigin=0;recEnd=total;}else{recOrigin=partStart;recStart=std::ceil((position+.00001)/barLength)*barLength;recEnd=partEnd;}if(recEnd>recStart){recordState=1;recording=1;}else{recordState=3;recording=3;}}
        anySoloSnapshot=false;for(int ch=0;ch<8;ch++)if(solo[ch])anySoloSnapshot=true;
        }
        for(int i=0;i<count;i++){
            if(playing){if(position>=0&&recordState==1&&position>=recStart){recordState=2;recording=2;if(currentVoice>=0)capture(1,currentVoice,90);}
                if(recordState==2&&position>=recEnd){if(currentVoice>=0)capture(0,currentVoice,0);recordState=3;recording=3;}
                if(!external&&position>=total){silenceBacking();position-=total;cursor=0;}
                double previous=position-tempo/(60.*rate);if(!external&&position>=0&&(std::floor(position/barLength)>std::floor(previous/barLength)))adopt();
                if(selected>=0&&position>=0){auto& events=plans[selected].events;while(cursor<events.size()&&events[cursor].beat<=position){dispatch(events[cursor]);cursor++;}}
                if(position<0){double phase=position-std::floor(position);countIn[i]=.1f*std::sin(2*lm::PI*1100*phase*60/tempo)*std::exp(-phase*80);}
            }
            if(mode==1&&rhythm&&playing&&heldRoot>=0&&position>=0){
                // Rhythm IDs match MusicTheory: hip-hop is the ninth groove.
                static constexpr double attacks[9][8]={{0,1,1.5,2.5,3,3.5,-1,-1},{0,.75,1.25,1.75,2,2.75,3.25,3.75},{0,2./3,1,1+2./3,2,2+2./3,3,3+2./3},{0,.75,1.5,2.5,3.25,-1,-1,-1},{0,.5,1,1.5,2,2.5,3,3.5},{0,.75,1.5,2.5,3.25,-1,-1,-1},{0,1,2,-1,-1,-1,-1,-1},{0,1.5,2.5,-1,-1,-1,-1,-1},{0,.75,1.5,2,2.75,3.5,-1,-1}};
                const int pattern=currentRhythm>=0&&currentRhythm<9?currentRhythm:0;
                double local=std::fmod(position,barLength);int attack=-1;for(int j=0;j<8;j++)if(attacks[pattern][j]>=0&&attacks[pattern][j]<=local)attack=j;
                double tick=std::floor(position/barLength)*8+attack;
                if(deferRhythm){lastRhythm=tick;deferRhythm=false;}
                else if(tick!=lastRhythm&&attack>=0){lastRhythm=tick;int output=heldRoot;
                    if(program/8==4){int offsets[]={0,intervals[voiceQuality][2],12,intervals[voiceQuality][2]};output=std::clamp(heldRoot+offsets[attack%4],0,127);}
                    trigger(output,std::clamp(int(heldVelocity*(attack%2==0?1.f:.83f)),1,127));outputNote=output;
                }
            }
            for(int j=0;j<pendingStrings;j++)if(onFrame[j]<=sample){tsf_channel_note_on(live,0,onNote[j],onVelocity[j]);voiceNotes[voiceCount++]=onNote[j];onFrame[j]=UINT64_MAX;}
            if(sample%32==0){for(int ch=0;ch<8;ch++){float target=(overrideTrack[ch]?gain[ch].load():partGain[ch])*(((overrideTrack[ch]&&mute[ch])||(anySoloSnapshot&&!solo[ch]))?0:1);smoothGain[ch]+=(target-smoothGain[ch])*.15f;tsf_channel_set_volume(backing,ch,smoothGain[ch]);tsf_channel_set_pan(backing,ch,std::clamp(.5f+(overrideTrack[ch]?pan[ch].load():partPan[ch])*.5f,0.f,1.f));}tsf_channel_midi_control(live,0,11,int(expression*127));tsf_channel_set_pitchwheel(live,0,std::clamp(8192+int(cents/200*8192),0,16383));}
            tsf_render_float(backing,accompaniment+i*2,1,0);tsf_render_float(live,instrument+i*2,1,0);
            if(playing)position+=tempo/(60.*rate);sample++;
        }beat=position;
    }
};
}
