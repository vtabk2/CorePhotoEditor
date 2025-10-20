#include "adjust_common.h"
#include <cmath>
#include <algorithm>

static const float hueAngles[8] = {0, 30, 60, 120, 180, 210, 270, 300};

static inline float clamp01(float v) {
    return std::max(0.0f, std::min(1.0f, v));
}

static inline void rgbToHsl(float r, float g, float b, float &h, float &s, float &l) {
    float maxv = std::max({r, g, b});
    float minv = std::min({r, g, b});
    float d = maxv - minv;
    l = (maxv + minv) / 2.0f;

    if (d < 1e-6f) {
        h = s = 0;
        return;
    }

    s = d / (1.0f - fabsf(2 * l - 1));
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
        if (t < 1.0f / 6) return p + (q - p) * 6 * t;
        if (t < 1.0f / 2) return q;
        if (t < 2.0f / 3) return p + (q - p) * (2.0f / 3 - t) * 6;
        return p;
    };

    s = clamp01(s);
    l = clamp01(l);

    if (s < 1e-6f) {
        r = g = b = l;
        return;
    }

    float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
    float p = 2 * l - q;
    r = hue2rgb(p, q, h / 360.0f + 1.0f / 3.0f);
    g = hue2rgb(p, q, h / 360.0f);
    b = hue2rgb(p, q, h / 360.0f - 1.0f / 3.0f);
}

extern "C" void applyHSL(float &r, float &g, float &b, const AdjustParams &p) {
    float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
    float h, s, l;
    rgbToHsl(rf, gf, bf, h, s, l);

    for (int i = 0; i < 8; i++) {
        float center = hueAngles[i];
        float diff = fabsf(h - center);
        if (diff > 180) diff = 360 - diff;
        if (diff < 30) {
            float t = 1.0f - (diff / 30.0f);
            h += p.hslHue[i] * t;
            s += p.hslSaturation[i] * t;
            l += p.hslLuminance[i] * t;
        }
    }

    if (h < 0) h += 360.0f;
    if (h > 360) h -= 360.0f;
    s = clamp01(s);
    l = clamp01(l);

    hslToRgb(h, s, l, rf, gf, bf);
    r = rf * 255.0f;
    g = gf * 255.0f;
    b = bf * 255.0f;
}