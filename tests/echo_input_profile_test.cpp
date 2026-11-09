// Execute production microphone startup against a recording AAudio builder adapter.
#include "../app/src/main/cpp/engine.cpp"
#include <cassert>
#include <iostream>
#include <memory>

static void check(int mode,int requestedInput,int actualOutput,int actualInput,bool enabled,bool defaultSafe,
        int expectedPreset,int expectedProfile,bool failCommunication=false,int actualPreset=0){
    host_audio::openEnabled=true;
    host_audio::actualInput=actualInput;
    host_audio::failCommunication=failCommunication;
    host_audio::actualPreset=actualPreset;
    host_audio::inputRequests.clear();
    AAudioStream output;output.device=actualOutput;
    auto e=std::make_unique<Engine>();
    e->output=&output;
    e->echoInputConfig.enabled=enabled;
    e->echoInputConfig.defaultInputSafe=defaultSafe;
    e->echoInputConfig.speakers={20};
    e->echoInputConfig.microphones={10};
    assert(e->openMicrophone(requestedInput,mode));
    assert(!host_audio::inputRequests.empty());
    auto first=host_audio::inputRequests.front(),last=host_audio::inputRequests.back();
    assert(last.first==expectedPreset);
    assert(e->inputProfile==expectedProfile);
    if(expectedPreset==AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION)assert(last.second==AAUDIO_PERFORMANCE_MODE_NONE);
    if(mode==0&&!enabled)assert(first.first==AAUDIO_INPUT_PRESET_VOICE_RECOGNITION);
    if(failCommunication)assert(first.first==AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION);
    // A retained input is not reconfigured when playback/mic starts are repeated.
    size_t requests=host_audio::inputRequests.size();
    assert(e->openMicrophone(requestedInput,mode));
    assert(host_audio::inputRequests.size()==requests);
    e->microphoneOff();assert(e->inputProfile==0);
}
int main(){
    const int recognition=AAUDIO_INPUT_PRESET_VOICE_RECOGNITION,communication=AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION;
    check(0,0,20,10,true,true,communication,3);
    check(0,10,20,10,true,false,communication,3); // Explicit built-in mic remains known with an external mic connected.
    for(int output:{0,21,22,23})check(0,10,output,10,true,true,recognition,1); // Unknown, headphones, USB and Bluetooth.
    check(0,10,20,10,false,true,recognition,1); // Preference off or AEC unavailable.
    check(0,0,20,10,true,false,recognition,1); // Default route ambiguous because an external input is present.
    check(0,99,20,99,true,true,recognition,1); // Explicit external microphone.
    check(0,0,20,99,true,true,recognition,1); // Input route changed after enumeration: reopen before starting.
    check(0,0,20,0,true,true,recognition,1); // Unknown opened microphone cannot keep the special preset.
    check(0,0,20,10,true,true,recognition,1,true); // Unsupported communication profile falls back to recognition.
    check(0,0,20,10,true,true,communication,1,false,recognition); // Report actual preset, not the requested preset.
    check(1,0,20,10,false,false,communication,2);
    check(2,99,23,99,false,false,communication,2); // Existing explicit mode behavior is unchanged.
    std::cout<<"PASS: actual built-in speaker/mic startup chooses echo-control input; ambiguous/external/off routes stay recognition; failed/overridden presets and cleanup reported honestly\n";
}
