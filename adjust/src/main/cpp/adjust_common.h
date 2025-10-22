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
    uint64_t activeMask;

    // --- HSL (Hue / Saturation / Luminance) ---
    float hslHue[8];
    float hslSaturation[8];
    float hslLuminance[8];

    // --- LUT FILTER SUPPORT ---
    std::string lutPath; // đường dẫn đến file LUT .table
};

static inline float clampf(float v, float lo = 0.f, float hi = 1.f) {
    return v < lo ? lo : (v > hi ? hi : v);
}
