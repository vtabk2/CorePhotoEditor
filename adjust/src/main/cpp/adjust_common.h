#pragma once

#include <cmath>
#include <algorithm>

struct AdjustParams {
    float exposure;
    float brightness;
    float contrast;
    float highlights;
    float shadows;
    float whites;
    float blacks;
    float temperature;
    float tint;
    float saturation;
    float vibrance;
    float texture;
    float clarity;
    float dehaze;
    float vignette;
    float grain;
};

inline float clampf(float v, float minv = 0.0f, float maxv = 1.0f) {
    return std::fmin(std::fmax(v, minv), maxv);
}
