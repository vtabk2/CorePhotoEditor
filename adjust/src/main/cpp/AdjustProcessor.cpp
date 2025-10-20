#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cmath>
#include "adjust_common.h"

extern void applyLightAdjust(float &, float &, float &, const AdjustParams &);

extern void applyColorAdjust(float &, float &, float &, const AdjustParams &);

extern void applyDetailAdjust(float &, float &, float &,
                              float, float, float, float,
                              const AdjustParams &);

extern "C" void applyVignetteAt(float &, float &, float &,
                                float, float, float, float,
                                const AdjustParams &);
extern "C" void applyGrainAt(float &, float &, float &, const AdjustParams &);

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AdjustNative", __VA_ARGS__)

// --- FNV-1a hash cho cache tham số ---
static inline uint64_t fnv64(uint64_t h, uint64_t v) {
    h ^= v;
    h *= 1099511628211ull;
    return h;
}

static inline uint64_t bitsOf(float f) {
    union {
        float f;
        uint32_t u;
    } x{f};
    return (uint64_t) x.u;
}

static inline uint64_t hashParams(const AdjustParams &p) {
    uint64_t h = 1469598103934665603ull;
    h = fnv64(h, bitsOf(p.exposure));
    h = fnv64(h, bitsOf(p.brightness));
    h = fnv64(h, bitsOf(p.contrast));
    h = fnv64(h, bitsOf(p.highlights));
    h = fnv64(h, bitsOf(p.shadows));
    h = fnv64(h, bitsOf(p.whites));
    h = fnv64(h, bitsOf(p.blacks));
    h = fnv64(h, bitsOf(p.temperature));
    h = fnv64(h, bitsOf(p.tint));
    h = fnv64(h, bitsOf(p.saturation));
    h = fnv64(h, bitsOf(p.vibrance));
    h = fnv64(h, bitsOf(p.texture));
    h = fnv64(h, bitsOf(p.clarity));
    h = fnv64(h, bitsOf(p.dehaze));
    h = fnv64(h, bitsOf(p.vignette));
    h = fnv64(h, bitsOf(p.grain));
    h = fnv64(h, (uint64_t) p.activeMask);
    return h;
}

static uint64_t s_lastHash = 0ull;

extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_clearCache(JNIEnv *, jclass) {
    s_lastHash = 0ull;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(
        JNIEnv *env, jclass, jobject bitmap, jobject paramsObj, jobject progressCb) {

    if (!bitmap || !paramsObj) return;

    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    // --- lấy params từ Kotlin ---
    jclass cls = env->GetObjectClass(paramsObj);
    auto getF = [&](const char *n) {
        jfieldID fid = env->GetFieldID(cls, n, "F");
        return fid ? env->GetFloatField(paramsObj, fid) : 0.f;
    };
    auto getJ = [&](const char *n) {
        jfieldID fid = env->GetFieldID(cls, n, "J");
        return fid ? env->GetLongField(paramsObj, fid) : (jlong) 0;
    };

    AdjustParams p{
            getF("exposure"), getF("brightness"), getF("contrast"),
            getF("highlights"), getF("shadows"), getF("whites"), getF("blacks"),
            getF("temperature"), getF("tint"), getF("saturation"),
            getF("vibrance"), getF("texture"),
            getF("clarity"), getF("dehaze"),
            getF("vignette"), getF("grain"),
            (uint64_t) getJ("activeMask")
    };

    // --- setup progress callback (tùy chọn) ---
    jclass cbCls = nullptr;
    jmethodID onProgress = nullptr;
    bool hasCb = (progressCb != nullptr);
    if (hasCb) {
        cbCls = env->GetObjectClass(progressCb);
        onProgress = cbCls ? env->GetMethodID(cbCls, "onProgress", "(I)V") : nullptr;
        if (!onProgress) hasCb = false;
        else env->CallVoidMethod(progressCb, onProgress, 0);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            hasCb = false;
        }
    }

    // --- skip sớm nếu không có filter nào bật ---
    if (p.activeMask == 0ull) {
        if (hasCb) {
            env->CallVoidMethod(progressCb, onProgress, 100);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // --- skip nếu params trùng lần trước ---
    uint64_t curHash = hashParams(p);
    if (curHash == s_lastHash) {
        if (hasCb) {
            env->CallVoidMethod(progressCb, onProgress, 100);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }
    s_lastHash = curHash;

    // --- vòng lặp áp filter ---
    const int W = (int) info.width;
    const int H = (int) info.height;
    const int strideBytes = (int) info.stride;

    uint8_t *line = static_cast<uint8_t *>(pixels);
    for (int y = 0; y < H; y++) {
        uint32_t *px = reinterpret_cast<uint32_t *>(line);
        for (int x = 0; x < W; x++) {
            uint32_t color = *px;
            uint8_t a = (color >> 24) & 0xFF;
            float r = (float) ((color >> 16) & 0xFF);
            float g = (float) ((color >> 8) & 0xFF);
            float b = (float) (color & 0xFF);

            if (p.activeMask & MASK_LIGHT) applyLightAdjust(r, g, b, p);

            float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;

            if (p.activeMask & MASK_COLOR) applyColorAdjust(rf, gf, bf, p);
            if (p.activeMask & MASK_DETAIL)
                applyDetailAdjust(rf, gf, bf,
                                  (float) x, (float) y, (float) W, (float) H, p);
            if (p.activeMask & MASK_VIGNETTE)
                applyVignetteAt(rf, gf, bf,
                                (float) x, (float) y, (float) W, (float) H, p);
            if (p.activeMask & MASK_GRAIN) applyGrainAt(rf, gf, bf, p);

            *px++ = ((uint32_t(a) << 24) |
                     (uint32_t(rf * 255.0f) << 16) |
                     (uint32_t(gf * 255.0f) << 8) |
                    (uint32_t) (bf * 255.0f));
        }

        line += strideBytes;

        // progress theo hàng: giảm tần suất JNI call
        if (hasCb && ((y % 3 == 0) || y == H - 1)) {
            int pct = (int) ((y + 1) * 100 / H);
            env->CallVoidMethod(progressCb, onProgress, pct);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                hasCb = false;
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

    if (hasCb) {
        env->CallVoidMethod(progressCb, onProgress, 100);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
}
