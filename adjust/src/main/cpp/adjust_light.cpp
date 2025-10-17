#include "adjust_common.h"

void applyLightAdjust(float &r, float &g, float &b, const AdjustParams &p) {
    float exposureFactor = powf(2.0f, p.exposure * 0.25f);
    r *= exposureFactor;
    g *= exposureFactor;
    b *= exposureFactor;

    if (p.brightness != 0.0f) {
        float factor = 1.0f + (p.brightness * 0.4f);
        r *= factor;
        g *= factor;
        b *= factor;
    }

    float contrastFactor = (p.contrast >= 0.0f)
                           ? 1.0f + (p.contrast * 0.8f)
                           : 1.0f / (1.0f - (p.contrast * 0.5f));
    r = ((r - 128.0f) * contrastFactor) + 128.0f;
    g = ((g - 128.0f) * contrastFactor) + 128.0f;
    b = ((b - 128.0f) * contrastFactor) + 128.0f;

    float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
    float luminance = 0.299f * rf + 0.587f * gf + 0.114f * bf;

    auto toneAdjust = [&](float factor, float low, float high) {
        if (factor != 0.0f) {
            if (luminance < low) {
                float boost = (low - luminance) * factor;
                rf += boost;
                gf += boost;
                bf += boost;
            }
            if (luminance > high) {
                float reduce = (luminance - high) * factor;
                rf -= reduce;
                gf -= reduce;
                bf -= reduce;
            }
        }
    };

    toneAdjust(p.shadows * 0.5f, 0.5f, 1.0f);
    toneAdjust(p.highlights * 0.5f, 0.0f, 0.5f);
    toneAdjust(p.whites * 0.7f, 0.0f, 0.65f);
    toneAdjust(p.blacks * 0.7f, 0.35f, 1.0f);

    r = rf * 255.0f;
    g = gf * 255.0f;
    b = bf * 255.0f;
}
