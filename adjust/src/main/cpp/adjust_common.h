#pragma once

#include <cmath>
#include <algorithm>
#include <string>

enum AdjustMask : uint64_t {
    MASK_LIGHT = 1ull << 0,
    MASK_COLOR = 1ull << 1,
    MASK_DETAIL = 1ull << 2,
    MASK_VIGNETTE = 1ull << 3,
    MASK_GRAIN = 1ull << 4,
    MASK_HSL = 1ull << 5,
    MASK_LUT = 1ull << 6,
};

struct AdjustParams {
    float exposure     = 0.f;
    float brightness   = 0.f;
    float contrast     = 0.f;
    float highlights   = 0.f;
    float shadows      = 0.f;
    float whites       = 0.f;
    float blacks       = 0.f;

    float temperature  = 0.f;
    float tint         = 0.f;
    float saturation   = 0.f;
    float vibrance     = 0.f;

    float texture      = 0.f;
    float clarity      = 0.f;
    float dehaze       = 0.f;

    float vignette     = 0.f;
    float grain        = 0.f;

    uint64_t activeMask = 0ull;

    // --- HSL ---
    float hslHue[8]        = {0.f};
    float hslSaturation[8] = {0.f};
    float hslLuminance[8]  = {0.f};

    // --- LUT ---
    std::string lutPath;
};

static inline float clampf(float v, float lo = 0.f, float hi = 1.f) {
    return v < lo ? lo : (v > hi ? hi : v);
}
