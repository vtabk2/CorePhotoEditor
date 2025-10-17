#include "adjust_common.h"

static inline void applyTexture(float &rf, float &gf, float &bf, const AdjustParams &p) {
    if (p.texture == 0.0f) return;
    float luminance = 0.299f * rf + 0.587f * gf + 0.114f * bf;
    float detail = (fabsf(rf - luminance) + fabsf(gf - luminance) + fabsf(bf - luminance)) / 3.0f;
    float factor = 1.0f + (p.texture * detail * 0.5f);
    rf = (rf - luminance) * factor + luminance;
    gf = (gf - luminance) * factor + luminance;
    bf = (bf - luminance) * factor + luminance;
}

static inline void applyClarity(float &rf, float &gf, float &bf, const AdjustParams &p) {
    if (p.clarity == 0.0f) return;

    // Tính độ sáng trung bình (midtone)
    float luminance = 0.299f * rf + 0.587f * gf + 0.114f * bf;

    // Clarity: tăng tương phản quanh midtone (0.5)
    float midtoneContrast = (luminance - 0.5f) * (1.0f + p.clarity * 1.2f);
    float factor = midtoneContrast - (luminance - 0.5f);

    // Điều chỉnh lại RGB theo hướng sáng/tối midtone
    rf += factor;
    gf += factor;
    bf += factor;

    // Nhấn nhẹ độ tương phản cục bộ (vi sai so với luminance)
    rf += (rf - luminance) * p.clarity * 0.5f;
    gf += (gf - luminance) * p.clarity * 0.5f;
    bf += (bf - luminance) * p.clarity * 0.5f;

    rf = clampf(rf);
    gf = clampf(gf);
    bf = clampf(bf);
}

static inline void applyDehaze(float &rf, float &gf, float &bf, const AdjustParams &p) {
    if (p.dehaze == 0.0f) return;
    float haze = (rf + gf + bf) / 3.0f;
    float boost = (p.dehaze > 0.0f ? (1.0f - haze) : haze) * fabsf(p.dehaze) * 0.6f;
    rf += (rf - haze) * boost;
    gf += (gf - haze) * boost;
    bf += (bf - haze) * boost;
    if (p.dehaze > 0.0f) {
        rf *= 1.02f;
        bf *= 0.98f;
    } else {
        rf *= 0.98f;
        bf *= 1.02f;
    }
}

void applyDetailAdjust(float &rf, float &gf, float &bf, const AdjustParams &p) {
    applyTexture(rf, gf, bf, p);
    applyClarity(rf, gf, bf, p);
    applyDehaze(rf, gf, bf, p);
    rf = clampf(rf);
    gf = clampf(gf);
    bf = clampf(bf);
}
