#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstring>
#include <android/log.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AdjustNative", __VA_ARGS__)

extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(
        JNIEnv *env, jclass /*clazz*/, jobject bitmap, jobject paramsObj) {

    if (bitmap == nullptr || paramsObj == nullptr) {
        LOGE("bitmap or params is null");
        return;
    }

    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock pixels");
        return;
    }

    // === Lấy các field từ AdjustParams ===
    jclass paramsCls = env->GetObjectClass(paramsObj);
    if (!paramsCls) {
        LOGE("Failed to find params class");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    auto getField = [&](const char *name) -> jfieldID {
        return env->GetFieldID(paramsCls, name, "F");
    };

    jfieldID exposureField = getField("exposure");
    jfieldID brightnessField = getField("brightness");
    jfieldID contrastField = getField("contrast");
    jfieldID saturationField = getField("saturation");
    jfieldID highlightsField = getField("highlights");
    jfieldID shadowsField = getField("shadows");

    float exposure = env->GetFloatField(paramsObj, exposureField);
    float brightness = env->GetFloatField(paramsObj, brightnessField);
    float contrast = env->GetFloatField(paramsObj, contrastField);
    float saturation = env->GetFloatField(paramsObj, saturationField);
    float highlights = env->GetFloatField(paramsObj, highlightsField);
    float shadows = env->GetFloatField(paramsObj, shadowsField);

    // === Chuẩn bị thông số ===
    // Giảm cường độ Exposure xuống cho mượt (x0.25)
    float exposureFactor = powf(2.0f, exposure * 0.25f);

    // Contrast mềm hơn, tránh xám ảnh
    float contrastFactor;
    if (contrast >= 0.0f) {
        contrastFactor = 1.0f + (contrast * 0.8f);
    } else {
        contrastFactor = 1.0f / (1.0f - (contrast * 0.5f));
    }

    float satAdj = saturation + 1.0f; // 0 giữ nguyên

    int width = info.width;
    int height = info.height;

    uint8_t *line = (uint8_t *) pixels;
    for (int y = 0; y < height; y++) {
        uint32_t *px = (uint32_t *) line;
        for (int x = 0; x < width; x++) {
            uint32_t color = *px;
            uint8_t a = (color >> 24) & 0xFF;
            float r = (color >> 16) & 0xFF;
            float g = (color >> 8) & 0xFF;
            float b = color & 0xFF;

            // --- Exposure ---
            r *= exposureFactor;
            g *= exposureFactor;
            b *= exposureFactor;

            // --- Brightness (mềm, -100..+100) ---
            // brightness ∈ [-1, 1] tương ứng slider [-100..100]
            if (brightness != 0.0f) {
                // 0.4f là độ nhạy: kéo 100 => sáng ~1.4x, kéo -100 => tối ~0.6x
                float brightnessFactor = 1.0f + (brightness * 0.4f);
                r *= brightnessFactor;
                g *= brightnessFactor;
                b *= brightnessFactor;
            }

            // --- Contrast ---
            r = ((r - 128.0f) * contrastFactor) + 128.0f;
            g = ((g - 128.0f) * contrastFactor) + 128.0f;
            b = ((b - 128.0f) * contrastFactor) + 128.0f;

            // --- Highlights & Shadows ---
            float rf = r / 255.0f;
            float gf = g / 255.0f;
            float bf = b / 255.0f;
            float luminance = 0.299f * rf + 0.587f * gf + 0.114f * bf;

            float shadowFactor = shadows * 0.5f;     // -1..+1
            float highlightFactor = highlights * 0.5f; // -1..+1

            // Tăng sáng vùng tối
            if (luminance < 0.5f && shadowFactor != 0.0f) {
                float boost = (0.5f - luminance) * shadowFactor;
                rf += boost;
                gf += boost;
                bf += boost;
            }
            // Giảm sáng vùng sáng
            if (luminance > 0.5f && highlightFactor != 0.0f) {
                float reduce = (luminance - 0.5f) * highlightFactor;
                rf -= reduce;
                gf -= reduce;
                bf -= reduce;
            }

            rf = fminf(fmaxf(rf, 0.0f), 1.0f);
            gf = fminf(fmaxf(gf, 0.0f), 1.0f);
            bf = fminf(fmaxf(bf, 0.0f), 1.0f);

            // --- Saturation ---
            float gray = 0.299f * rf + 0.587f * gf + 0.114f * bf;
            rf = gray + (rf - gray) * satAdj;
            gf = gray + (gf - gray) * satAdj;
            bf = gray + (bf - gray) * satAdj;

            // Clamp và scale lại 0-255
            r = fminf(fmaxf(rf * 255.0f, 0.0f), 255.0f);
            g = fminf(fmaxf(gf * 255.0f, 0.0f), 255.0f);
            b = fminf(fmaxf(bf * 255.0f, 0.0f), 255.0f);

            *px++ = ((a << 24) | ((uint8_t) r << 16) | ((uint8_t) g << 8) | (uint8_t) b);
        }
        line += info.stride;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
