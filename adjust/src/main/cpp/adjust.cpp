#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <android/log.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AdjustNative", __VA_ARGS__)

// ===========================================================
// 🔆 LIGHT GROUP
// ===========================================================
static void applyLightAdjust(float &r, float &g, float &b,
                             float exposure, float brightness, float contrast,
                             float highlights, float shadows,
                             float whites, float blacks) {

    // Exposure
    float exposureFactor = powf(2.0f, exposure * 0.25f);
    r *= exposureFactor;
    g *= exposureFactor;
    b *= exposureFactor;

    // Brightness
    if (brightness != 0.0f) {
        float brightnessFactor = 1.0f + (brightness * 0.4f);
        r *= brightnessFactor;
        g *= brightnessFactor;
        b *= brightnessFactor;
    }

    // Contrast
    float contrastFactor = (contrast >= 0.0f)
                           ? 1.0f + (contrast * 0.8f)
                           : 1.0f / (1.0f - (contrast * 0.5f));
    r = ((r - 128.0f) * contrastFactor) + 128.0f;
    g = ((g - 128.0f) * contrastFactor) + 128.0f;
    b = ((b - 128.0f) * contrastFactor) + 128.0f;

    // Highlights / Shadows / Whites / Blacks
    float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
    float luminance = 0.299f * rf + 0.587f * gf + 0.114f * bf;

    auto adjustTone = [&](float factor, float thresholdLow, float thresholdHigh) {
        if (factor != 0.0f) {
            if (luminance < thresholdLow) {
                float boost = (thresholdLow - luminance) * factor;
                rf += boost;
                gf += boost;
                bf += boost;
            }
            if (luminance > thresholdHigh) {
                float reduce = (luminance - thresholdHigh) * factor;
                rf -= reduce;
                gf -= reduce;
                bf -= reduce;
            }
        }
    };

    adjustTone(shadows * 0.5f, 0.5f, 1.0f);
    adjustTone(highlights * 0.5f, 0.0f, 0.5f);
    adjustTone(whites * 0.7f, 0.0f, 0.65f);
    adjustTone(blacks * 0.7f, 0.35f, 1.0f);

    r = rf * 255.0f;
    g = gf * 255.0f;
    b = bf * 255.0f;
}

// ===========================================================
// 🎨 COLOR GROUP
// ===========================================================
static void applyColorAdjust(float &rf, float &gf, float &bf,
                             float temperature, float tint,
                             float saturation, float vibrance) {

    // Temperature
    if (temperature != 0.0f) {
        float warm = temperature * 0.25f;
        rf += warm;
        bf -= warm;
    }

    // Tint
    if (tint != 0.0f) {
        float shift = tint * 0.25f;
        gf -= shift;
        rf += shift * 0.5f;
        bf += shift * 0.5f;
    }

    // Vibrance
    if (vibrance != 0.0f) {
        float gray = 0.299f * rf + 0.587f * gf + 0.114f * bf;
        float maxC = fmaxf(rf, fmaxf(gf, bf));
        float vib = vibrance * (1.0f - maxC);
        rf = gray + (rf - gray) * (1.0f + vib);
        gf = gray + (gf - gray) * (1.0f + vib);
        bf = gray + (bf - gray) * (1.0f + vib);
    }

    // Saturation
    if (saturation != 0.0f) {
        float gray = 0.299f * rf + 0.587f * gf + 0.114f * bf;
        rf = gray + (rf - gray) * (1.0f + saturation);
        gf = gray + (gf - gray) * (1.0f + saturation);
        bf = gray + (bf - gray) * (1.0f + saturation);
    }
}

// ===========================================================
// 🔵 DETAIL GROUP
// ===========================================================
static void applyDetailAdjust(float &rf, float &gf, float &bf,
                              float texture) {
    if (texture != 0.0f) {
        float luminance = 0.299f * rf + 0.587f * gf + 0.114f * bf;
        float detail = (fabsf(rf - luminance) +
                        fabsf(gf - luminance) +
                        fabsf(bf - luminance)) / 3.0f;
        float textureFactor = 1.0f + (texture * detail * 0.5f);
        rf = (rf - luminance) * textureFactor + luminance;
        gf = (gf - luminance) * textureFactor + luminance;
        bf = (bf - luminance) * textureFactor + luminance;
    }
}

// ===========================================================
// 🚀 MAIN JNI ENTRY
// ===========================================================
extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(
        JNIEnv *env, jclass, jobject bitmap, jobject paramsObj) {

    if (!bitmap || !paramsObj) return;

    AndroidBitmapInfo info;
    void *pixels;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    jclass paramsCls = env->GetObjectClass(paramsObj);
    auto getField = [&](const char *name) -> jfieldID {
        return env->GetFieldID(paramsCls, name, "F");
    };

    // --- Lấy giá trị từ params ---
    float exposure = env->GetFloatField(paramsObj, getField("exposure"));
    float brightness = env->GetFloatField(paramsObj, getField("brightness"));
    float contrast = env->GetFloatField(paramsObj, getField("contrast"));
    float highlights = env->GetFloatField(paramsObj, getField("highlights"));
    float shadows = env->GetFloatField(paramsObj, getField("shadows"));
    float whites = env->GetFloatField(paramsObj, getField("whites"));
    float blacks = env->GetFloatField(paramsObj, getField("blacks"));
    float temperature = env->GetFloatField(paramsObj, getField("temperature"));
    float tint = env->GetFloatField(paramsObj, getField("tint"));
    float saturation = env->GetFloatField(paramsObj, getField("saturation"));
    float vibrance = env->GetFloatField(paramsObj, getField("vibrance"));
    float texture = env->GetFloatField(paramsObj, getField("texture"));

    uint8_t *line = static_cast<uint8_t *>(pixels);
    for (int y = 0; y < info.height; y++) {
        uint32_t *px = reinterpret_cast<uint32_t *>(line);
        for (int x = 0; x < info.width; x++) {
            uint32_t color = *px;
            uint8_t a = (color >> 24) & 0xFF;
            float r = static_cast<float>((color >> 16) & 0xFF);
            float g = static_cast<float>((color >> 8) & 0xFF);
            float b = static_cast<float>(color & 0xFF);

            // --- Light group ---
            applyLightAdjust(r, g, b, exposure, brightness, contrast, highlights, shadows, whites,
                             blacks);

            float rf = r / 255.0f;
            float gf = g / 255.0f;
            float bf = b / 255.0f;

            // --- Color group ---
            applyColorAdjust(rf, gf, bf, temperature, tint, saturation, vibrance);

            // --- Detail group ---
            applyDetailAdjust(rf, gf, bf, texture);

            rf = fminf(fmaxf(rf, 0.0f), 1.0f);
            gf = fminf(fmaxf(gf, 0.0f), 1.0f);
            bf = fminf(fmaxf(bf, 0.0f), 1.0f);

            *px++ = ((uint32_t(a) << 24) |
                     (uint32_t(rf * 255.0f) << 16) |
                     (uint32_t(gf * 255.0f) << 8) |
                     uint32_t(bf * 255.0f));
        }
        line += info.stride;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}