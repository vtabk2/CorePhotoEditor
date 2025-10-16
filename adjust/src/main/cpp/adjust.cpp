\


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
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Unsupported bitmap format: %d", info.format);
        // still try if RGBA_8888 expected
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock pixels");
        return;
    }

    // Get AdjustParams fields
    jclass paramsCls = env->GetObjectClass(paramsObj);
    if (!paramsCls) {
        LOGE("Failed to find params class");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    jfieldID exposureField = env->GetFieldID(paramsCls, "exposure", "F");
    jfieldID brightnessField = env->GetFieldID(paramsCls, "brightness", "F");
    jfieldID contrastField = env->GetFieldID(paramsCls, "contrast", "F");
    jfieldID saturationField = env->GetFieldID(paramsCls, "saturation", "F");

    if (!exposureField || !brightnessField || !contrastField || !saturationField) {
        LOGE("Failed to get one or more field IDs");
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    float exposure = env->GetFloatField(paramsObj, exposureField);
    float brightness = env->GetFloatField(paramsObj, brightnessField);
    float contrast = env->GetFloatField(paramsObj, contrastField);
    float saturation = env->GetFloatField(paramsObj, saturationField);

    // Precompute factors
    // Giảm độ nhạy exposure xuống (0.25f = giảm tác động còn 1/4)
    // -> slider.value = 500 => exposure = 5.0 => 5 * 0.25 = 1.25 -> pow(2,1.25)=2.38x
    // trước kia là pow(2,5)=32x (quá cháy)
    float exposureFactor = powf(2.0f, exposure * 0.25f);
    float brightnessOffset = brightness * 255.0f;
    float contrastFactor = contrast + 1.0f; // 0 -> unchanged
    float satAdj = saturation + 1.0f; // 0 -> unchanged

    int width = info.width;
    int height = info.height;

    // Handle RGBA_8888 and RGB_565 (basic support)
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        uint8_t *line = (uint8_t *) pixels;
        for (int y = 0; y < height; y++) {
            uint32_t *px = (uint32_t *) line;
            for (int x = 0; x < width; x++) {
                uint32_t color = *px;
                uint8_t a = (color >> 24) & 0xFF;
                float r = (color >> 16) & 0xFF;
                float g = (color >> 8) & 0xFF;
                float b = color & 0xFF;

                // Exposure
                r *= exposureFactor;
                g *= exposureFactor;
                b *= exposureFactor;

                // clamp sớm để tránh overflow sớm
                r = fminf(fmaxf(r, 0.0f), 255.0f);
                g = fminf(fmaxf(g, 0.0f), 255.0f);
                b = fminf(fmaxf(b, 0.0f), 255.0f);

                // Brightness
                r += brightnessOffset;
                g += brightnessOffset;
                b += brightnessOffset;

                // Contrast
                r = ((r - 128.0f) * contrastFactor) + 128.0f;
                g = ((g - 128.0f) * contrastFactor) + 128.0f;
                b = ((b - 128.0f) * contrastFactor) + 128.0f;

                // Saturation (convert to luma and lerp)
                float gray = 0.299f * r + 0.587f * g + 0.114f * b;
                r = gray + (r - gray) * satAdj;
                g = gray + (g - gray) * satAdj;
                b = gray + (b - gray) * satAdj;

                // Clamp
                int ir = (int) (r < 0.0f ? 0.0f : (r > 255.0f ? 255.0f : r));
                int ig = (int) (g < 0.0f ? 0.0f : (g > 255.0f ? 255.0f : g));
                int ib = (int) (b < 0.0f ? 0.0f : (b > 255.0f ? 255.0f : b));

                *px++ = ((a & 0xFF) << 24) | ((ir & 0xFF) << 16) | ((ig & 0xFF) << 8) | (ib & 0xFF);
            }
            line += info.stride;
        }
    } else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565) {
        // Basic support for RGB_565: modify in-place by converting to 8-bit components
        uint8_t *line = (uint8_t *) pixels;
        for (int y = 0; y < height; y++) {
            uint16_t *px = (uint16_t *) line;
            for (int x = 0; x < width; x++) {
                uint16_t packed = *px;
                int r5 = (packed >> 11) & 0x1F;
                int g6 = (packed >> 5) & 0x3F;
                int b5 = packed & 0x1F;

                float r = (r5 * 255.0f) / 31.0f;
                float g = (g6 * 255.0f) / 63.0f;
                float b = (b5 * 255.0f) / 31.0f;

                r *= exposureFactor;
                g *= exposureFactor;
                b *= exposureFactor;

                r += brightnessOffset;
                g += brightnessOffset;
                b += brightnessOffset;

                r = ((r - 128.0f) * contrastFactor) + 128.0f;
                g = ((g - 128.0f) * contrastFactor) + 128.0f;
                b = ((b - 128.0f) * contrastFactor) + 128.0f;

                float gray = 0.299f * r + 0.587f * g + 0.114f * b;
                r = gray + (r - gray) * satAdj;
                g = gray + (g - gray) * satAdj;
                b = gray + (b - gray) * satAdj;

                int ir = (int) (r < 0.0f ? 0.0f : (r > 255.0f ? 255.0f : r));
                int ig = (int) (g < 0.0f ? 0.0f : (g > 255.0f ? 255.0f : g));
                int ib = (int) (b < 0.0f ? 0.0f : (b > 255.0f ? 255.0f : b));

                int r5n = (ir * 31) / 255;
                int g6n = (ig * 63) / 255;
                int b5n = (ib * 31) / 255;

                uint16_t outPacked = (r5n << 11) | (g6n << 5) | b5n;
                *px++ = outPacked;
            }
            line += info.stride;
        }
    } else {
        // Unsupported format: do nothing
        LOGE("Unsupported bitmap format, skipping");
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}
