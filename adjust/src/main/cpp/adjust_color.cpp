#include "adjust_common.h"

void applyColorAdjust(float &rf, float &gf, float &bf, const AdjustParams &p) {
    if (p.temperature != 0.0f) {
        float warm = p.temperature * 0.25f;
        rf += warm;
        bf -= warm;
    }
    if (p.tint != 0.0f) {
        float shift = p.tint * 0.25f;
        gf -= shift;
        rf += shift * 0.5f;
        bf += shift * 0.5f;
    }
    if (p.vibrance != 0.0f) {
        float gray = 0.299f * rf + 0.587f * gf + 0.114f * bf;
        float maxC = fmaxf(rf, fmaxf(gf, bf));
        float vib = p.vibrance * (1.0f - maxC);
        rf = gray + (rf - gray) * (1.0f + vib);
        gf = gray + (gf - gray) * (1.0f + vib);
        bf = gray + (bf - gray) * (1.0f + vib);
    }
    if (p.saturation != 0.0f) {
        float gray = 0.299f * rf + 0.587f * gf + 0.114f * bf;
        rf = gray + (rf - gray) * (1.0f + p.saturation);
        gf = gray + (gf - gray) * (1.0f + p.saturation);
        bf = gray + (bf - gray) * (1.0f + p.saturation);
    }
    rf = clampf(rf);
    gf = clampf(gf);
    bf = clampf(bf);
}
