#include "feedback_guard.h"
#include <iostream>
using namespace lm;
int main() {
    float wave[2048];
    for(int sr:{44100,48000}) {
        for(float hz:{250.f,400.f,1500.f,6000.f}) {
            FeedbackGuard guard; guard.init(sr,true); int frame=0;
            for(float amplitude:{.05f,.075f,.12f,.19f,.3f}) {
                for(int i=0;i<2048;i++)wave[i]=amplitude*std::sin(2*PI*hz*i/sr);
                guard.analyze(wave,2048,sr,.1*frame++);
            }
            guard.prepare(sr);double filtered=0,original=0;
            for(int i=0;i<sr/2;i++){
                float x=.3f*std::sin(2*PI*hz*i/sr),left=x,right=x;
                guard.process(left,right);
                if(i>sr/4){filtered+=left*left+right*right;original+=2*x*x;}
            }
            std::cout<<"growth sr="<<sr<<" hz="<<hz<<" count="<<guard.count()<<" muted="<<guard.muted()<<" energy_ratio="<<filtered/original<<'\n';
        }
        FeedbackGuard guard;guard.init(sr,true);int frame=0;
        for(int cycle=1;cycle<=3;cycle++) {
            for(float amplitude:{.03f,.05f,.08f,.14f}) {
                for(int i=0;i<2048;i++)wave[i]=amplitude*std::sin(2*PI*1500*i/sr);
                guard.analyze(wave,2048,sr,.1*frame++);
            }
            std::cout<<"repeat sr="<<sr<<" cycle="<<cycle<<" count="<<guard.count()<<" muted="<<guard.muted()<<'\n';
        }
    }
}
