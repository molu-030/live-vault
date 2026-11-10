#pragma once
#include <algorithm>
#include <cmath>
namespace lm {
// FIFO servo, not a hardware clock/timestamp measurement. Callback-owned.
class InputDrift {
    double target=0,inputRate=48000,filtered=0,integral=0,correction=0;
public:
    static constexpr double limitPpm=1000;
    static constexpr double slewPpmPerSecond=50;
    void reset(int rate,double targetFrames){inputRate=rate;target=targetFrames;filtered=integral=correction=0;}
    double ppm()const{return correction;}
    void update(double bufferedFrames,int outputFrames,int outputRate){
        if(outputFrames<=0||outputRate<=0)return;
        double dt=double(outputFrames)/outputRate,error=bufferedFrames-target;
        // Integer delivery/startup rounding move fill by at most one input frame
        // or one output frame's input duration with perfectly matched clocks.
        if(std::abs(error)<=std::max(1.0,inputRate/outputRate)+1e-6)error=0;
        error/=inputRate;
        filtered+=(error-filtered)*(dt/(.8+dt));
        integral=std::clamp(integral+filtered*20000*dt,-limitPpm,limitPpm);
        double wanted=std::clamp(filtered*300000+integral,-limitPpm,limitPpm);
        correction+=std::clamp(wanted-correction,-slewPpmPerSecond*dt,slewPpmPerSecond*dt);
    }
};
}
