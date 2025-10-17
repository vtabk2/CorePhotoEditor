#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "adjust_common.h"

extern void applyLightAdjust(float &, float &, float &, const AdjustParams &);

extern void applyColorAdjust(float &, float &, float &, const AdjustParams &);

extern void applyDetailAdjust(float &, float &, float &, const AdjustParams &);

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AdjustNative", __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(
        JNIEnv *env, jclass, jobject bitmap, jobject paramsObj) {

    if (!bitmap || !paramsObj) return;

    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    jclass cls = env->GetObjectClass(paramsObj);
    auto getF = [&](const char *n) {
        return env->GetFloatField(paramsObj, env->GetFieldID(cls, n, "F"));
    };

    AdjustParams p{
            getF("exposure"), getF("brightness"), getF("contrast"),
            getF("highlights"), getF("shadows"), getF("whites"), getF("blacks"),
            getF("temperature"), getF("tint"), getF("saturation"),
            getF("vibrance"), getF("texture"),
            getF("clarity"), getF("dehaze")
    };

    uint8_t *line = static_cast<uint8_t *>(pixels);
    for (int y = 0; y < info.height; y++) {
        uint32_t *px = reinterpret_cast<uint32_t *>(line);
        for (int x = 0; x < info.width; x++) {
            uint32_t color = *px;
            uint8_t a = (color >> 24) & 0xFF;
            float r = (float) ((color >> 16) & 0xFF);
            float g = (float) ((color >> 8) & 0xFF);
            float b = (float) (color & 0xFF);

            applyLightAdjust(r, g, b, p);
            float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
            applyColorAdjust(rf, gf, bf, p);
            applyDetailAdjust(rf, gf, bf, p);

            *px++ = ((uint32_t(a) << 24) |
                     (uint32_t(rf * 255.0f) << 16) |
                     (uint32_t(gf * 255.0f) << 8) |
                     uint32_t(bf * 255.0f));
        }
        line += info.stride;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}
