#include "adjust_common.h"
#include <algorithm>
#include <cmath>

// ======================= LUT Gamma Table =======================
static bool s_gammaInit = false;
static float s_srgbToLinearLUT[256];
static float s_linearToSrgbLUT[256];

// Khởi tạo bảng tra cứu gamma (256 giá trị)
static void initGammaLUT() {
    if (s_gammaInit) return;
    for (int i = 0; i < 256; i++) {
        float c = i / 255.0f;
        // sRGB -> Linear
        s_srgbToLinearLUT[i] = (c <= 0.04045f)
                               ? (c / 12.92f)
                               : powf((c + 0.055f) / 1.055f, 2.4f);
        // Linear -> sRGB
        s_linearToSrgbLUT[i] = (c <= 0.0031308f)
                               ? (12.92f * c)
                               : (1.055f * powf(c, 1.0f / 2.4f) - 0.055f);
    }
    s_gammaInit = true;
}

static inline float srgbToLinear(float c) {
    return (c <= 0.0f) ? 0.0f : (c >= 1.0f ? 1.0f : s_srgbToLinearLUT[int(c * 255.0f)]);
}

static inline float linearToSrgb(float c) {
    return (c <= 0.0f) ? 0.0f : (c >= 1.0f ? 1.0f : s_linearToSrgbLUT[int(c * 255.0f)]);
}

// ======================= Tone Curve ============================
static inline float toneMapCurve(float x) {
    // curve mềm hơn kiểu Lightroom "Medium Contrast"
    return std::clamp(x + 0.15f * sinf((x - 0.5f) * 3.1415926f), 0.0f, 1.0f);
}

// ======================= Main Adjust ===========================
void applyLightAdjust(float &r, float &g, float &b, const AdjustParams &p) {
    initGammaLUT();

    // ---- Normalize về [0,1] ----
    r /= 255.0f;
    g /= 255.0f;
    b /= 255.0f;

    // ---- sRGB -> Linear ----
    r = srgbToLinear(r);
    g = srgbToLinear(g);
    b = srgbToLinear(b);

    // ---- Exposure ----
    float exposureFactor = powf(2.0f, p.exposure * 0.5f);
    r *= exposureFactor;
    g *= exposureFactor;
    b *= exposureFactor;

    // ---- Contrast ---- (xử lý trước brightness)
    float contrastFactor = (p.contrast >= 0.0f)
                           ? 1.0f + (p.contrast * 0.8f)
                           : 1.0f / (1.0f - (p.contrast * 0.5f));

    r = ((r - 0.5f) * contrastFactor) + 0.5f;
    g = ((g - 0.5f) * contrastFactor) + 0.5f;
    b = ((b - 0.5f) * contrastFactor) + 0.5f;

    // ---- Brightness ---- (midtone lift)
    if (p.brightness != 0.0f) {
        float factor = 1.0f + (p.brightness * 0.2f);
        r = (r - 0.5f) * factor + 0.5f;
        g = (g - 0.5f) * factor + 0.5f;
        b = (b - 0.5f) * factor + 0.5f;
    }

    // ---- Clamp ----
    r = std::clamp(r, 0.0f, 1.0f);
    g = std::clamp(g, 0.0f, 1.0f);
    b = std::clamp(b, 0.0f, 1.0f);

    // ---- Tone Adjust (cập nhật luminance mỗi lần) ----
    auto toneAdjust = [&](float factor, float low, float high) {
        if (factor != 0.0f) {
            float lum = 0.299f * r + 0.587f * g + 0.114f * b;
            if (lum < low) {
                float boost = (low - lum) * factor;
                r += boost;
                g += boost;
                b += boost;
            }
            if (lum > high) {
                float reduce = (lum - high) * factor;
                r -= reduce;
                g -= reduce;
                b -= reduce;
            }
        }
    };

    toneAdjust(p.shadows * 0.5f, 0.5f, 1.0f);
    toneAdjust(p.highlights * 0.5f, 0.0f, 0.5f);
    toneAdjust(p.whites * 0.7f, 0.0f, 0.65f);
    toneAdjust(p.blacks * 0.7f, 0.35f, 1.0f);

    // ---- Tone mapping midtone ----
    r = toneMapCurve(r);
    g = toneMapCurve(g);
    b = toneMapCurve(b);

    // ---- Clamp và chuyển ngược về sRGB ----
    r = linearToSrgb(std::clamp(r, 0.0f, 1.0f));
    g = linearToSrgb(std::clamp(g, 0.0f, 1.0f));
    b = linearToSrgb(std::clamp(b, 0.0f, 1.0f));

    // ---- Scale về [0,255] ----
    r *= 255.0f;
    g *= 255.0f;
    b *= 255.0f;
}
