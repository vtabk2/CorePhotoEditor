#include "adjust_common.h"
#include <cmath>
#include <algorithm>

// ======================= Hue Region Centers =======================
static const float hueCenters[8] = {0, 30, 60, 120, 180, 210, 270, 300};

// ======================= Clamp Helper =======================
static inline float clamp01(float v) {
    return std::max(0.0f, std::min(1.0f, v));
}

// ======================= sRGB <-> Linear =======================
static inline float srgbToLinear(float c) {
    return (c <= 0.04045f) ? (c / 12.92f)
                           : powf((c + 0.055f) / 1.055f, 2.4f);
}
static inline float linearToSrgb(float c) {
    return (c <= 0.0031308f) ? (12.92f * c)
                             : (1.055f * powf(c, 1.0f / 2.4f) - 0.055f);
}

// ======================= RGB <-> HSL =======================
static inline void rgbToHsl(float r, float g, float b, float &h, float &s, float &l) {
    float maxv = std::max({r, g, b});
    float minv = std::min({r, g, b});
    float d = maxv - minv;
    l = (maxv + minv) * 0.5f;

    if (d < 1e-6f) {
        h = s = 0.0f;
        return;
    }

    s = d / (1.0f - fabsf(2.0f * l - 1.0f));
    if (maxv == r)
        h = 60.0f * fmodf(((g - b) / d), 6.0f);
    else if (maxv == g)
        h = 60.0f * (((b - r) / d) + 2.0f);
    else
        h = 60.0f * (((r - g) / d) + 4.0f);

    if (h < 0) h += 360.0f;
}

static inline void hslToRgb(float h, float s, float l, float &r, float &g, float &b) {
    auto hue2rgb = [](float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6 * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6;
        return p;
    };

    s = clamp01(s);
    l = clamp01(l);

    if (s < 1e-6f) {
        r = g = b = l;
        return;
    }

    float q = (l < 0.5f) ? (l * (1 + s)) : (l + s - l * s);
    float p = 2 * l - q;
    r = hue2rgb(p, q, h / 360.0f + 1.0f / 3.0f);
    g = hue2rgb(p, q, h / 360.0f);
    b = hue2rgb(p, q, h / 360.0f - 1.0f / 3.0f);
}

// ======================= Main HSL Adjust =======================
extern "C" void applyHSLAdjust(float &r, float &g, float &b, const AdjustParams &p) {
    // ---- Normalize & linearize ----
    float rf = srgbToLinear(r / 255.0f);
    float gf = srgbToLinear(g / 255.0f);
    float bf = srgbToLinear(b / 255.0f);

    // ---- Convert RGB -> HSL ----
    float h, s, l;
    rgbToHsl(rf, gf, bf, h, s, l);

    // ---- Weighted HSL adjustment (normalized blending) ----
    float totalW = 0.0f;
    float hueShift = 0.0f, satShift = 0.0f, lumShift = 0.0f;

    for (int i = 0; i < 8; i++) {
        float center = hueCenters[i];
        float diff = fabsf(h - center);
        if (diff > 180.0f) diff = 360.0f - diff;
        if (diff < 30.0f) {
            float w = 1.0f - (diff / 30.0f);
            hueShift += p.hslHue[i] * w;
            satShift += p.hslSaturation[i] * w;
            lumShift += p.hslLuminance[i] * w;
            totalW += w;
        }
    }

    if (totalW > 0.0f) {
        hueShift /= totalW;
        satShift /= totalW;
        lumShift /= totalW;

        // ±30° Hue, ±100% Sat, ±100% Lum
        h += hueShift * 30.0f;
        s *= (1.0f + satShift);
        l *= (1.0f + lumShift);
    }

    // ---- Clamp & wrap hue ----
    if (h < 0.0f) h += 360.0f;
    else if (h >= 360.0f) h -= 360.0f;
    s = clamp01(s);
    l = clamp01(l);

    // ---- Convert HSL -> RGB ----
    hslToRgb(h, s, l, rf, gf, bf);

    // ---- Back to sRGB & scale ----
    r = linearToSrgb(clamp01(rf)) * 255.0f;
    g = linearToSrgb(clamp01(gf)) * 255.0f;
    b = linearToSrgb(clamp01(bf)) * 255.0f;
}
