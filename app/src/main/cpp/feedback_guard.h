#pragma once
#include "audio_core.h"
namespace lm {
// Conservative protection for a microphone feeding a loudspeaker. Not a substitute for
// physical separation, a sensible device volume, or the device's acoustic echo canceller.
class FeedbackGuard {
    std::atomic<bool> enabled{false},tripped{false},resetRequested{false};
    std::array<std::atomic<float>,4> frequencies{};
    Biquad filters[2][4];float cached[4]{},blend[4]{};
    std::array<float,1024> bins{},coefficients{};std::array<double,1024> powers{};std::array<float,2048> window{};
    int binCount=0,sampleRate=0;
    float candidate=0,initialLevel=0;int held=0,loud=0,next=0,incidents=0;double lastIncident=-100;
    void clearDetector(){candidate=initialLevel=0;held=loud=next=incidents=0;lastIncident=-100;for(auto& f:frequencies)f=0;}
public:
    void init(int sr,bool on,bool preserveTrip=false){enabled=on;if(!preserveTrip)tripped=false;resetRequested=false;clearDetector();
        sampleRate=sr;binCount=0;
        if(sr<8000||sr>384000){enabled=false;return;}
        // Uniform analysis bins avoid both overlapping low-frequency probes and
        // gaps wider than the window's main lobe at high frequencies.
        for(int k=1;k<1024;k++){
            float f=float(double(k)*sr/2048);
            if(f<150-sr/2048.f)continue;
            if(f>std::min(8000.f,sr*.45f)+sr/2048.f)break;
            bins[binCount]=f;coefficients[binCount++]=2*std::cos(2*PI*f/sr);
        }
        for(int i=0;i<2048;i++)window[i]=.5f-.5f*std::cos(2*PI*i/2047);
        for(int i=0;i<4;i++){cached[i]=blend[i]=0;filters[0][i]=Biquad{};filters[1][i]=Biquad{};}
    }
    // Route changes clear detector history on its worker, while retaining an
    // existing safety latch until the user explicitly resets it.
    void setEnabled(bool on){if(enabled.exchange(on)!=on)resetRequested=true;}
    bool isEnabled()const{return enabled;}
    void reset(){tripped=false;resetRequested=true;}
    bool muted()const{return enabled&&tripped;}
    int count()const{int n=0;for(auto& f:frequencies)if(f.load()>0)n++;return n;}
    void analyze(const float* input,int n,int sr,double seconds){
        if(!enabled)return;
        if(resetRequested.exchange(false))clearDetector();
        if(!input||n!=2048||sr!=sampleRate||binCount==0||!std::isfinite(seconds)){held=loud=0;return;}
        if(tripped)return;
        double energy=0,windowedEnergy=0;for(int i=0;i<n;i++){
            if(!std::isfinite(input[i])){held=loud=0;return;}
            energy+=double(input[i])*input[i];
            double sample=double(input[i])*window[i];windowedEnergy+=sample*sample;
        }float level=std::sqrt(energy/n);
        if(level>.65f){if(++loud>=2)tripped=true;}else loud=0;
        if(level<.025f){held=0;return;}
        // Parseval includes energy outside the searched band, so bass leakage
        // cannot masquerade as a dominant tone at the lowest analysis bin.
        double positiveEnergy=windowedEnergy*n*.5;
        if(!std::isfinite(positiveEnergy)||positiveEnergy<=0){held=0;return;}
        double sum=0,best=0;int index=0;
        for(int k=0;k<binCount;k++){
            double q1=0,q2=0;for(int i=0;i<n;i++){double q=input[i]*window[i]+coefficients[k]*q1-q2;q2=q1;q1=q;}
            double power=std::max(0.,q1*q1+q2*q2-coefficients[k]*q1*q2);powers[k]=power;sum+=power;if(power>best){best=power;index=k;}
        }
        double peak=0;for(int k=std::max(0,index-1);k<=std::min(binCount-1,index+1);k++)peak+=powers[k];
        if(sum<=0||best/sum<.23||peak/positiveEnergy<.85){held=0;return;}
        float tone=bins[index];
        // Estimate between analysis bins so a narrow low-frequency notch is
        // centered on the tone, rather than up to half a bin away from it.
        if(index>0&&index+1<binCount&&powers[index-1]>0&&powers[index+1]>0){
            double a=std::log(powers[index-1]),b=std::log(best),c=std::log(powers[index+1]);
            double denominator=a-2*b+c;
            if(std::isfinite(denominator)&&std::abs(denominator)>1e-12){
                double offset=std::clamp(.5*(a-c)/denominator,-.5,.5);
                tone+=float(offset*sr/2048);
            }
        }
        tone=std::clamp(tone,150.f,std::min(8000.f,sr*.45f));
        if(held==0||std::abs(tone-candidate)>candidate*.045f){candidate=tone;initialLevel=level;held=1;return;}
        held++;
        if(held>=3&&level>initialLevel*1.6f){
            bool known=false;for(auto& f:frequencies)if(std::abs(f.load()-tone)<tone*.08f)known=true;
            if(!known)frequencies[next++%4]=tone;
            // Re-arm even for an existing notch: another fresh 1.6x rise at the
            // same frequency is an incident, rather than bypassing escalation.
            held=0;
            incidents=seconds-lastIncident<10?incidents+1:1;lastIncident=seconds;
            if(incidents>=3)tripped=true;
        }
    }
    void prepare(int sr){if(sr!=sampleRate||sr<=0)return;for(int i=0;i<4;i++){
        float f=frequencies[i];
        if(f!=cached[i]){filters[0][i]=Biquad{};filters[1][i]=Biquad{};}
        if(f>0&&f!=cached[i]){
            float w=2*PI*f/sr,alpha=std::sin(w)/(2*18.f),a0=1+alpha;
            for(int ch=0;ch<2;ch++){auto& b=filters[ch][i];b=Biquad{};b.b0=1/a0;b.b1=-2*std::cos(w)/a0;b.b2=1/a0;b.a1=b.b1;b.a2=(1-alpha)/a0;}
        }cached[i]=f;
    }}
    void process(float& left,float& right){if(!enabled)return;
        if(!std::isfinite(left)||!std::isfinite(right)){left=right=0;tripped=true;return;}
        for(int i=0;i<4;i++){
            float target=cached[i]>0?.88f:0;blend[i]+=(target-blend[i])*.002f;
            if(blend[i]>.0001f){left+=(filters[0][i].tick(left)-left)*blend[i];right+=(filters[1][i].tick(right)-right)*blend[i];}
        }
        if(!std::isfinite(left)||!std::isfinite(right))tripped=true;
        if(tripped){left=right=0;return;}
        left=std::clamp(left,-.45f,.45f);right=std::clamp(right,-.45f,.45f);
    }
};
}
