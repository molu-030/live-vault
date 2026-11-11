#pragma once
#include <algorithm>
#include <vector>

// Startup-only policy. Unknown routes never opt a recognition input into communication processing.
struct EchoInputProfile {
    bool enabled = false, defaultInputSafe = false;
    std::vector<int> speakers, microphones;
    static bool contains(const std::vector<int>& ids, int id) {
        return id > 0 && std::find(ids.begin(), ids.end(), id) != ids.end();
    }
    bool builtinMicrophone(int id) const { return contains(microphones, id); }
    bool useFor(int input, int actualOutput) const {
        return enabled && contains(speakers, actualOutput)
                && (input > 0 ? builtinMicrophone(input) : defaultInputSafe && !microphones.empty());
    }
};
