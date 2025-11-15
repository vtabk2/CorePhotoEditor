#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <thread>
#include <queue>
#include <condition_variable>
#include <atomic>
#include <functional>
#include <cmath>
#include <algorithm>
#include <chrono>
#include <fstream>
#include <cstring>
#include <string>
#include <cstdint>

#include "adjust_common.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#define LOG_TAG "TAG5"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static inline void DeleteLocalRefSafely(JNIEnv *env, jobject obj) {
    if (obj) env->DeleteLocalRef(obj);
}

// =============================================================
// 🎨 LUT 3D TABLE SUPPORT
// =============================================================
struct Lut3D {
    int32_t size = 0;
    std::vector<float> data; // size^3 * 3
    bool valid() const {
        const size_t need = static_cast<size_t>(size) * static_cast<size_t>(size) * static_cast<size_t>(size) * 3u;
        return size > 0 && data.size() == need;
    }
};

static bool loadTableFile(JNIEnv *env, jobject context, const std::string &path, Lut3D &lut) {
    // 1️⃣ Try open from normal file
    std::ifstream f(path, std::ios::binary);
    if (f.is_open()) {
        uint32_t header[2];
        f.read(reinterpret_cast<char *>(header), sizeof(header));
        if (!f) {
            LOGE("Failed to read LUT header: %s", path.c_str());
            return false;
        }

        lut.size = static_cast<int32_t>(header[0]);
        const size_t count = static_cast<size_t>(lut.size) * lut.size * lut.size * 3u;
        lut.data.resize(count);
        f.read(reinterpret_cast<char *>(lut.data.data()), count * sizeof(float));
        f.close();

        if (lut.valid()) {
            LOGI("✅ LUT loaded from file: %s (size=%d)", path.c_str(), lut.size);
            return true;
        }
        LOGE("Invalid LUT content in file: %s", path.c_str());
        return false;
    }

    // 2️⃣ Try open from assets
    LOGI("File not found, try assets/%s", path.c_str());

    jclass ctxCls = env->GetObjectClass(context);
    jmethodID getAssets = env->GetMethodID(ctxCls, "getAssets",
                                           "()Landroid/content/res/AssetManager;");
    jobject assetMgrObj = env->CallObjectMethod(context, getAssets);
    DeleteLocalRefSafely(env, ctxCls);

    AAssetManager *mgr = AAssetManager_fromJava(env, assetMgrObj);
    DeleteLocalRefSafely(env, assetMgrObj);
    if (!mgr) {
        LOGE("AAssetManager is null");
        return false;
    }

    AAsset *asset = AAssetManager_open(mgr, path.c_str(), AASSET_MODE_STREAMING);
    if (!asset) {
        LOGE("LUT not found in assets: %s", path.c_str());
        return false;
    }

    uint32_t header[2];
    if (AAsset_read(asset, header, sizeof(header)) != sizeof(header)) {
        LOGE("Failed to read LUT header from asset: %s", path.c_str());
        AAsset_close(asset);
        return false;
    }

    lut.size = static_cast<int32_t>(header[0]);
    const size_t count = static_cast<size_t>(lut.size) * lut.size * lut.size * 3u;
    lut.data.resize(count);
    const ssize_t bytesRead = AAsset_read(asset, lut.data.data(), count * sizeof(float));
    AAsset_close(asset);

    if (bytesRead != static_cast<ssize_t>(count * sizeof(float))) {
        LOGE("Failed to read LUT data from asset: %s", path.c_str());
        return false;
    }

    if (lut.valid()) {
        LOGI("✅ LUT loaded from assets/%s (size=%d)", path.c_str(), lut.size);
        return true;
    }
    LOGE("Invalid LUT data from asset: %s", path.c_str());
    return false;
}

static inline void sampleLUT(const Lut3D &lut, float r, float g, float b,
                             float &rr, float &gg, float &bb) {
    const int32_t S = lut.size;
    if (S <= 1) { rr = r; gg = g; bb = b; return; }

    const float rf = r * static_cast<float>(S - 1);
    const float gf = g * static_cast<float>(S - 1);
    const float bf = b * static_cast<float>(S - 1);

    const int32_t r0 = static_cast<int32_t>(floorf(rf));
    const int32_t g0 = static_cast<int32_t>(floorf(gf));
    const int32_t b0 = static_cast<int32_t>(floorf(bf));
    const int32_t r1 = std::min<int32_t>(r0 + 1, S - 1);
    const int32_t g1 = std::min<int32_t>(g0 + 1, S - 1);
    const int32_t b1 = std::min<int32_t>(b0 + 1, S - 1);

    const float wr = rf - static_cast<float>(r0);
    const float wg = gf - static_cast<float>(g0);
    const float wb = bf - static_cast<float>(b0);

    auto idx = [&](int32_t ir, int32_t ig, int32_t ib) -> size_t {
        return (static_cast<size_t>(ir) * static_cast<size_t>(S) + static_cast<size_t>(ig)) * static_cast<size_t>(S) + static_cast<size_t>(ib);
    };

    const size_t i000 = idx(r0, g0, b0) * 3u;
    const size_t i001 = idx(r0, g0, b1) * 3u;
    const size_t i010 = idx(r0, g1, b0) * 3u;
    const size_t i011 = idx(r0, g1, b1) * 3u;
    const size_t i100 = idx(r1, g0, b0) * 3u;
    const size_t i101 = idx(r1, g0, b1) * 3u;
    const size_t i110 = idx(r1, g1, b0) * 3u;
    const size_t i111 = idx(r1, g1, b1) * 3u;

    const float c000r = lut.data[i000 + 0], c000g = lut.data[i000 + 1], c000b = lut.data[i000 + 2];
    const float c001r = lut.data[i001 + 0], c001g = lut.data[i001 + 1], c001b = lut.data[i001 + 2];
    const float c010r = lut.data[i010 + 0], c010g = lut.data[i010 + 1], c010b = lut.data[i010 + 2];
    const float c011r = lut.data[i011 + 0], c011g = lut.data[i011 + 1], c011b = lut.data[i011 + 2];
    const float c100r = lut.data[i100 + 0], c100g = lut.data[i100 + 1], c100b = lut.data[i100 + 2];
    const float c101r = lut.data[i101 + 0], c101g = lut.data[i101 + 1], c101b = lut.data[i101 + 2];
    const float c110r = lut.data[i110 + 0], c110g = lut.data[i110 + 1], c110b = lut.data[i110 + 2];
    const float c111r = lut.data[i111 + 0], c111g = lut.data[i111 + 1], c111b = lut.data[i111 + 2];

    // along b
    const float c00r = c000r * (1.0f - wb) + c001r * wb;
    const float c00g = c000g * (1.0f - wb) + c001g * wb;
    const float c00b = c000b * (1.0f - wb) + c001b * wb;

    const float c01r = c010r * (1.0f - wb) + c011r * wb;
    const float c01g = c010g * (1.0f - wb) + c011g * wb;
    const float c01b = c010b * (1.0f - wb) + c011b * wb;

    const float c10r = c100r * (1.0f - wb) + c101r * wb;
    const float c10g = c100g * (1.0f - wb) + c101g * wb;
    const float c10b = c100b * (1.0f - wb) + c101b * wb;

    const float c11r = c110r * (1.0f - wb) + c111r * wb;
    const float c11g = c110g * (1.0f - wb) + c111g * wb;
    const float c11b = c110b * (1.0f - wb) + c111b * wb;

    // along g
    const float c0r = c00r * (1.0f - wg) + c01r * wg;
    const float c0g = c00g * (1.0f - wg) + c01g * wg;
    const float c0b = c00b * (1.0f - wg) + c01b * wg;

    const float c1r = c10r * (1.0f - wg) + c11r * wg;
    const float c1g = c10g * (1.0f - wg) + c11g * wg;
    const float c1b = c10b * (1.0f - wg) + c11b * wg;

    // along r
    rr = c0r * (1.0f - wr) + c1r * wr;
    gg = c0g * (1.0f - wr) + c1g * wr;
    bb = c0b * (1.0f - wr) + c1b * wr;
}

// --- extern modules (must match your project)
extern void applyLightAdjust(float &r, float &g, float &b, const AdjustParams &p);
extern "C" void applyHSLAdjust(float &r, float &g, float &b, const AdjustParams &p);
extern void applyColorAdjust(float &rf, float &gf, float &bf, const AdjustParams &p);
extern void applyDetailAdjust(float &rf, float &gf, float &bf, float x, float y, float width, float height, const AdjustParams &p);
extern "C" void applyVignetteAt(float &rf, float &gf, float &bf, float x, float y, float w, float h, const AdjustParams &p);
extern "C" void applyGrainAt(float &rf, float &gf, float &bf, const AdjustParams &p);

// =============================================================
// 🧵 ThreadPool
// =============================================================
class ThreadPool {
public:
    explicit ThreadPool(size_t n) { start(n); }
    ~ThreadPool() { stopAll(); }

    void enqueue(std::function<void()> task) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            tasks_.push(std::move(task));
        }
        cv_.notify_one();
    }

    void waitAll() {
        std::unique_lock<std::mutex> lock(waitMutex_);
        waitCv_.wait(lock, [this] { return active_ == 0 && tasks_.empty(); });
    }

private:
    std::vector<std::thread> workers_;
    std::queue<std::function<void()>> tasks_;
    std::mutex mutex_;
    std::condition_variable cv_;
    std::atomic<bool> stop_{false};
    std::atomic<int32_t> active_{0};
    std::mutex waitMutex_;
    std::condition_variable waitCv_;

    void start(size_t n) {
        for (size_t i = 0; i < n; ++i) {
            workers_.emplace_back([this] {
                while (true) {
                    std::function<void()> task;
                    {
                        std::unique_lock<std::mutex> lock(mutex_);
                        cv_.wait(lock, [this] { return stop_.load() || !tasks_.empty(); });
                        if (stop_.load() && tasks_.empty()) return;
                        task = std::move(tasks_.front());
                        tasks_.pop();
                        active_.fetch_add(1, std::memory_order_relaxed);
                    }
                    try { task(); } catch (...) {}
                    {
                        std::lock_guard<std::mutex> lk(waitMutex_);
                        active_.fetch_sub(1, std::memory_order_relaxed);
                        if (active_.load(std::memory_order_relaxed) == 0 && tasks_.empty()) {
                            waitCv_.notify_all();
                        }
                    }
                }
            });
        }
    }

    void stopAll() {
        stop_.store(true);
        cv_.notify_all();
        for (auto &t: workers_) if (t.joinable()) t.join();
        std::lock_guard<std::mutex> lock(mutex_);
        while (!tasks_.empty()) tasks_.pop();
    }
};

// =============================================================
// 🌍 Globals
// =============================================================
static ThreadPool *gPool = nullptr;
static std::atomic<uint64_t> s_lastHash{0ull};
static std::string s_lastLutPath; // cache LUT path đã apply gần nhất

// =============================================================
// ⚙️ JNI helpers
// =============================================================

static float getFieldF(JNIEnv *env, jobject obj, const char *name) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, name, "F");
    const float v = fid ? env->GetFloatField(obj, fid) : 0.0f;
    DeleteLocalRefSafely(env, cls);
    return v;
}

static jfloatArray getFloatArray(JNIEnv *env, jobject obj, const char *name) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, name, "[F");
    jfloatArray arr = fid ? static_cast<jfloatArray>(env->GetObjectField(obj, fid)) : nullptr;
    DeleteLocalRefSafely(env, cls);
    return arr;
}

static uint64_t getLongField(JNIEnv *env, jobject obj, const char *name) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, name, "J");
    const uint64_t v = fid ? static_cast<uint64_t>(env->GetLongField(obj, fid)) : 0ull;
    DeleteLocalRefSafely(env, cls);
    return v;
}

static void loadParamsFromJava(JNIEnv *env, jobject paramsObj, AdjustParams &p) {
    if (!paramsObj) return;

    // Light
    p.exposure   = getFieldF(env, paramsObj, "exposure");
    p.brightness = getFieldF(env, paramsObj, "brightness");
    p.contrast   = getFieldF(env, paramsObj, "contrast");
    p.highlights = getFieldF(env, paramsObj, "highlights");
    p.shadows    = getFieldF(env, paramsObj, "shadows");
    p.whites     = getFieldF(env, paramsObj, "whites");
    p.blacks     = getFieldF(env, paramsObj, "blacks");

    // Color
    p.temperature = getFieldF(env, paramsObj, "temperature");
    p.tint        = getFieldF(env, paramsObj, "tint");
    p.vibrance    = getFieldF(env, paramsObj, "vibrance");
    p.saturation  = getFieldF(env, paramsObj, "saturation");

    // Detail
    p.texture = getFieldF(env, paramsObj, "texture");
    p.clarity = getFieldF(env, paramsObj, "clarity");
    p.dehaze  = getFieldF(env, paramsObj, "dehaze");

    // Effects
    p.vignette = getFieldF(env, paramsObj, "vignette");
    p.grain    = getFieldF(env, paramsObj, "grain");

    // HSL arrays
    jfloatArray hueArr = getFloatArray(env, paramsObj, "hslHue");
    jfloatArray satArr = getFloatArray(env, paramsObj, "hslSaturation");
    jfloatArray lumArr = getFloatArray(env, paramsObj, "hslLuminance");
    if (hueArr) {
        const jsize len = std::min<jsize>(8, env->GetArrayLength(hueArr));
        env->GetFloatArrayRegion(hueArr, 0, len, p.hslHue);
        DeleteLocalRefSafely(env, hueArr);
    }
    if (satArr) {
        const jsize len = std::min<jsize>(8, env->GetArrayLength(satArr));
        env->GetFloatArrayRegion(satArr, 0, len, p.hslSaturation);
        DeleteLocalRefSafely(env, satArr);
    }
    if (lumArr) {
        const jsize len = std::min<jsize>(8, env->GetArrayLength(lumArr));
        env->GetFloatArrayRegion(lumArr, 0, len, p.hslLuminance);
        DeleteLocalRefSafely(env, lumArr);
    }

    p.activeMask = getLongField(env, paramsObj, "activeMask");

    // --- LUT specific ---
    p.lutAmount = getFieldF(env, paramsObj, "lutAmount");
    // clamp defensively
    p.lutAmount = clampf(p.lutAmount, 0.f, 1.f);

    // Clamp input defensively
    p.vignette = clampf(p.vignette, 0.f, 1.f);
    p.grain    = std::max(0.f, p.grain);
}

// =============================================================
// 🧮 Hash (bao gồm LUT nếu có bật MASK_LUT)
// =============================================================
static inline uint64_t bitsOfFloat(float f) {
    union { float f; uint32_t u; } x{f};
    return static_cast<uint64_t>(x.u);
}

static uint64_t computeAdjustHash(const AdjustParams &p) {
    uint64_t h = 1469598103934665603ull; // FNV-1a basis
    auto mix = [&](uint64_t v) { h ^= v; h *= 1099511628211ull; }; // FNV-1a prime

    mix(bitsOfFloat(p.exposure));
    mix(bitsOfFloat(p.brightness));
    mix(bitsOfFloat(p.contrast));
    mix(bitsOfFloat(p.highlights));
    mix(bitsOfFloat(p.shadows));
    mix(bitsOfFloat(p.whites));
    mix(bitsOfFloat(p.blacks));
    mix(bitsOfFloat(p.temperature));
    mix(bitsOfFloat(p.tint));
    mix(bitsOfFloat(p.vibrance));
    mix(bitsOfFloat(p.saturation));
    mix(bitsOfFloat(p.texture));
    mix(bitsOfFloat(p.clarity));
    mix(bitsOfFloat(p.dehaze));
    mix(bitsOfFloat(p.vignette));
    mix(bitsOfFloat(p.grain));
    for (int i = 0; i < 8; ++i) {
        mix(bitsOfFloat(p.hslHue[i]));
        mix(bitsOfFloat(p.hslSaturation[i]));
        mix(bitsOfFloat(p.hslLuminance[i]));
    }
    mix(p.activeMask);

    // 🔗 MIX LUT khi có bật MASK_LUT và có đường dẫn
    if ((p.activeMask & MASK_LUT) && !p.lutPath.empty()) {
        for (unsigned char c : p.lutPath) mix(static_cast<uint64_t>(c));
        // ✅ tham gia cả lutAmount để đổi slider vẫn re-render
        mix(bitsOfFloat(p.lutAmount));
    }
    return h;
}

static bool isNoOp(const AdjustParams& p, bool hasLut) {
    const auto nearZero = [](float v) { return std::fabs(v) < 1e-4f; };

    if (p.activeMask == 0) return true;

    if (p.activeMask & MASK_LIGHT)
        if (!nearZero(p.exposure) || !nearZero(p.brightness) || !nearZero(p.contrast) ||
            !nearZero(p.highlights) || !nearZero(p.shadows) || !nearZero(p.whites) || !nearZero(p.blacks))
            return false;

    if (p.activeMask & MASK_COLOR)
        if (!nearZero(p.temperature) || !nearZero(p.tint) || !nearZero(p.vibrance) || !nearZero(p.saturation))
            return false;

    if (p.activeMask & MASK_DETAIL)
        if (!nearZero(p.texture) || !nearZero(p.clarity) || !nearZero(p.dehaze))
            return false;

    if (p.activeMask & MASK_HSL)
        for (int i = 0; i < 8; ++i)
            if (!nearZero(p.hslHue[i]) || !nearZero(p.hslSaturation[i]) || !nearZero(p.hslLuminance[i]))
                return false;

    if ((p.activeMask & MASK_VIGNETTE) && !nearZero(p.vignette)) return false;
    if ((p.activeMask & MASK_GRAIN) && !nearZero(p.grain))       return false;

    // 🔄 LUT chỉ có tác dụng khi amount khác 0
    if ((p.activeMask & MASK_LUT) && hasLut && !nearZero(p.lutAmount)) return false;

    return true;
}

// =============================================================
// 🧮 processRange (adjust stage)
// =============================================================
static void processRange(void *basePixels,
                         int64_t start, int64_t end,
                         int32_t width, int32_t height,
                         size_t strideBytes,
                         const AdjustParams &p,
                         std::atomic<int64_t> &doneCounter,
                         bool premultiplied) {
    auto *base = reinterpret_cast<uint8_t *>(basePixels);

    for (int64_t idx = start; idx < end; ++idx) {
        const int32_t y = static_cast<int32_t>(idx / width);
        const int32_t x = static_cast<int32_t>(idx % width);

        auto *row = reinterpret_cast<uint32_t *>(base + static_cast<size_t>(y) * strideBytes);
        const uint32_t color = row[x];

        const uint8_t au = static_cast<uint8_t>((color >> 24) & 0xFFu);
        const uint8_t ru = static_cast<uint8_t>((color >> 16) & 0xFFu);
        const uint8_t gu = static_cast<uint8_t>((color >>  8) & 0xFFu);
        const uint8_t bu = static_cast<uint8_t>( color        & 0xFFu);

        float a = static_cast<float>(au);
        float r = static_cast<float>(ru);
        float g = static_cast<float>(gu);
        float b = static_cast<float>(bu);

        // Un-premultiply if needed
        if (premultiplied && a > 0.0f) {
            const float af = a / 255.0f;
            const float inv = (af > 0.0f ? (1.0f / af) : 0.0f);
            r = std::min(255.0f, r * inv);
            g = std::min(255.0f, g * inv);
            b = std::min(255.0f, b * inv);
        }

        if (p.activeMask & MASK_LIGHT) applyLightAdjust(r, g, b, p);
        if (p.activeMask & MASK_HSL)   applyHSLAdjust(r, g, b, p);

        r = std::clamp(r, 0.0f, 255.0f);
        g = std::clamp(g, 0.0f, 255.0f);
        b = std::clamp(b, 0.0f, 255.0f);

        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        if (p.activeMask & MASK_COLOR)    applyColorAdjust(rf, gf, bf, p);
        if (p.activeMask & MASK_DETAIL)   applyDetailAdjust(rf, gf, bf, static_cast<float>(x), static_cast<float>(y), static_cast<float>(width), static_cast<float>(height), p);
        if (p.activeMask & MASK_VIGNETTE) applyVignetteAt(rf, gf, bf, static_cast<float>(x), static_cast<float>(y), static_cast<float>(width), static_cast<float>(height), p);
        if (p.activeMask & MASK_GRAIN)    applyGrainAt(rf, gf, bf, p);

        rf = std::clamp(rf, 0.0f, 1.0f);
        gf = std::clamp(gf, 0.0f, 1.0f);
        bf = std::clamp(bf, 0.0f, 1.0f);

        // Re-premultiply if needed
        float rout, gout, bout;
        if (premultiplied && a > 0.0f) {
            const float af = a / 255.0f;
            rout = std::clamp(rf * 255.0f * af, 0.0f, 255.0f);
            gout = std::clamp(gf * 255.0f * af, 0.0f, 255.0f);
            bout = std::clamp(bf * 255.0f * af, 0.0f, 255.0f);
        } else {
            rout = rf * 255.0f;
            gout = gf * 255.0f;
            bout = bf * 255.0f;
        }

        row[x] = (static_cast<uint32_t>(au) << 24)
                 | (static_cast<uint32_t>(static_cast<uint8_t>(rout)) << 16)
                 | (static_cast<uint32_t>(static_cast<uint8_t>(gout)) <<  8)
                 |  static_cast<uint32_t>(static_cast<uint8_t>(bout));

        doneCounter.fetch_add(1, std::memory_order_relaxed);
    }
}

// =============================================================
// 🎨 processLutRange (LUT stage, multi-thread)
// =============================================================
static void processLutRange(void *basePixels,
                            int64_t start, int64_t end,
                            int32_t width,
                            size_t strideBytes,
                            const AdjustParams &p,
                            const Lut3D &lut,
                            bool premultiplied) {
    auto *base = reinterpret_cast<uint8_t *>(basePixels);
    const float t = clampf(p.lutAmount, 0.f, 1.f);

    for (int64_t idx = start; idx < end; ++idx) {
        const int32_t y = static_cast<int32_t>(idx / width);
        const int32_t x = static_cast<int32_t>(idx % width);

        auto *row = reinterpret_cast<uint32_t *>(base + static_cast<size_t>(y) * strideBytes);
        const uint32_t c = row[x];

        const uint8_t a = static_cast<uint8_t>((c >> 24) & 0xFFu);
        float r = static_cast<float>((c >> 16) & 0xFFu) / 255.0f;
        float g = static_cast<float>((c >>  8) & 0xFFu) / 255.0f;
        float b = static_cast<float>( c        & 0xFFu) / 255.0f;

        float rOrig = r, gOrig = g, bOrig = b;

        if (premultiplied && a > 0u) {
            const float af  = static_cast<float>(a) / 255.0f;
            const float inv = (af > 0.0f ? (1.0f / af) : 0.0f);
            r = std::min(1.0f, r * inv);
            g = std::min(1.0f, g * inv);
            b = std::min(1.0f, b * inv);
            rOrig = std::min(1.0f, rOrig * inv);
            gOrig = std::min(1.0f, gOrig * inv);
            bOrig = std::min(1.0f, bOrig * inv);
        }

        float rr, gg, bb;
        sampleLUT(lut, r, g, b, rr, gg, bb);

        rr = std::clamp(rr, 0.0f, 1.0f);
        gg = std::clamp(gg, 0.0f, 1.0f);
        bb = std::clamp(bb, 0.0f, 1.0f);

        // blend theo lutAmount
        rr = rOrig * (1.0f - t) + rr * t;
        gg = gOrig * (1.0f - t) + gg * t;
        bb = bOrig * (1.0f - t) + bb * t;

        if (premultiplied && a > 0u) {
            const float af = static_cast<float>(a) / 255.0f;
            rr = std::clamp(rr * af, 0.0f, 1.0f);
            gg = std::clamp(gg * af, 0.0f, 1.0f);
            bb = std::clamp(bb * af, 0.0f, 1.0f);
        }

        row[x] = (static_cast<uint32_t>(a) << 24)
                 | (static_cast<uint32_t>(static_cast<uint8_t>(rr * 255.0f)) << 16)
                 | (static_cast<uint32_t>(static_cast<uint8_t>(gg * 255.0f)) <<  8)
                 |  static_cast<uint32_t>(static_cast<uint8_t>(bb * 255.0f));
    }
}

// =============================================================
// 🔗 JNI: applyAdjustNative
// =============================================================
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(JNIEnv *env, jobject /*thiz*/,
                                                       jobject context,
                                                       jobject bitmap,
                                                       jobject paramsObj, jobject progressCb) {
    if (!bitmap || !paramsObj) return JNI_FALSE;

    // Initialize thread pool on demand
    if (!gPool) {
        const unsigned int hw = std::thread::hardware_concurrency();
        const unsigned int n  = std::max(2u, (hw > 0 ? hw / 2 : 2u));
        gPool = new ThreadPool(static_cast<size_t>(n));
    }

    // 1) Load params
    AdjustParams p{};
    loadParamsFromJava(env, paramsObj, p);

    // 2) Read LUT path from paramsObj.lutPath and store into p.lutPath
    std::string lutPath;
    {
        jclass cls = env->GetObjectClass(paramsObj);
        jfieldID lutField = cls ? env->GetFieldID(cls, "lutPath", "Ljava/lang/String;") : nullptr;
        if (lutField) {
            jstring jstr = static_cast<jstring>(env->GetObjectField(paramsObj, lutField));
            if (jstr) {
                const char *cstr = env->GetStringUTFChars(jstr, nullptr);
                if (cstr) { lutPath.assign(cstr); env->ReleaseStringUTFChars(jstr, cstr); }
                DeleteLocalRefSafely(env, jstr);
            }
        }
        DeleteLocalRefSafely(env, cls);
    }
    p.lutPath = lutPath; // must set before hashing

    // 3) Hash after we have lutPath
    const uint64_t hash = computeAdjustHash(p);
    const uint64_t last = s_lastHash.load(std::memory_order_relaxed);
    if (hash == last) {
        LOGI("🔁 Same hash detected — skip all processing");
        return JNI_FALSE; // do not call progress on skip
    }
    s_lastHash.store(hash, std::memory_order_relaxed);

    // 4) No-op guard (reset = 0 or LUT amount == 0)
    const bool hasLut = ((p.activeMask & MASK_LUT) && !lutPath.empty());
    if (isNoOp(p, hasLut)) {
        LOGI("No-op: all params 0 or LUT amount==0 -> skip");
        return JNI_FALSE;
    }

    // 5) Prepare progress callback
    jmethodID onProgress = nullptr;
    if (progressCb) {
        jclass cbCls = env->GetObjectClass(progressCb);
        if (cbCls) onProgress = env->GetMethodID(cbCls, "onProgress", "(I)V");
        DeleteLocalRefSafely(env, cbCls);
    }

    // 6) Bitmap info
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    const bool premultiplied = (info.flags & ANDROID_BITMAP_FLAGS_ALPHA_PREMUL) != 0;

    // ---------------------------------------------------------
    // 🎨 LUT Stage (apply BEFORE other adjusts) + lutAmount blend
    // ---------------------------------------------------------
    if ((p.activeMask & MASK_LUT) && !lutPath.empty() && p.lutAmount > 0.0f) {
        LOGI("🎨 Applying LUT from path: %s with lutAmount=%.3f", lutPath.c_str(), p.lutAmount);
        Lut3D lut;
        if (loadTableFile(env, context, lutPath, lut)) {
            s_lastLutPath = lutPath; // remember last LUT path

            const int32_t W = static_cast<int32_t>(info.width);
            const int32_t H = static_cast<int32_t>(info.height);
            const int64_t total = static_cast<int64_t>(W) * static_cast<int64_t>(H);
            const size_t stride = static_cast<size_t>(info.stride);

            const unsigned int nThreads = std::max(1u, std::thread::hardware_concurrency());
            const int64_t chunk = (total + static_cast<int64_t>(nThreads) - 1) / static_cast<int64_t>(nThreads);

            for (unsigned int tIdx = 0; tIdx < nThreads; ++tIdx) {
                const int64_t start = static_cast<int64_t>(tIdx) * chunk;
                const int64_t end   = std::min<int64_t>(total, start + chunk);
                if (start >= end) break;

                gPool->enqueue([pixels, premultiplied, start, end, W, stride, &p, &lut]() {
                    processLutRange(pixels, start, end, W, stride, p, lut, premultiplied);
                });
            }

            gPool->waitAll();
            LOGI("✅ LUT applied successfully (multi-thread)");
        } else {
            LOGE("❌ Failed to load LUT file: %s", lutPath.c_str());
        }
    } else {
        LOGI("⚠️ No LUT stage (mask off, empty path, or lutAmount==0)");
    }

    // Sau khi đã áp LUT (nếu có), vẫn có thể còn mask khác
    uint64_t effectiveMask = p.activeMask;

    // Bỏ LUT ra để xem còn mask nào khác không
    const uint64_t nonLutMask = effectiveMask & ~MASK_LUT;

    // Nếu chỉ có LUT, không còn LIGHT/COLOR/DETAIL... thì không cần pass thứ 2
    if (nonLutMask == 0) {
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGI("Only LUT active -> skip adjust stage");
        return JNI_TRUE; // ảnh đã thay đổi thật sự
    }

    // ---------------------------------------------------------
    // APPLY ADJUSTS (multi-threaded) cho các mask còn lại (không gồm LUT)
    // ---------------------------------------------------------
    const int32_t W = static_cast<int32_t>(info.width);
    const int32_t H = static_cast<int32_t>(info.height);
    const int64_t total = static_cast<int64_t>(W) * static_cast<int64_t>(H);
    const size_t stride = static_cast<size_t>(info.stride);

    std::atomic<int64_t> doneCounter{0};
    const unsigned int nThreads = std::max(1u, std::thread::hardware_concurrency());
    const int64_t chunk = (total + static_cast<int64_t>(nThreads) - 1) / static_cast<int64_t>(nThreads);

    // tạo bản sao params chỉ chứa nonLutMask
    AdjustParams p2 = p;
    p2.activeMask = nonLutMask;

    for (unsigned int t = 0; t < nThreads; ++t) {
        const int64_t start = static_cast<int64_t>(t) * chunk;
        const int64_t end   = std::min<int64_t>(total, start + chunk);
        if (start >= end) break;
        gPool->enqueue([p2, pixels, premultiplied, start, end, W, H, stride, &doneCounter]() {
            processRange(pixels, start, end, W, H, stride, p2, doneCounter, premultiplied);
        });
    }

    // Progress polling (giảm spam: sleep lâu hơn, chỉ update khi nhảy >= 3%)
    int32_t lastPct = 0;
    while (doneCounter.load(std::memory_order_relaxed) < total) {
        std::this_thread::sleep_for(std::chrono::milliseconds(12));
        const int64_t done = doneCounter.load(std::memory_order_relaxed);
        const int32_t pct = static_cast<int32_t>((done * 100) / std::max<int64_t>(total, 1));
        if (onProgress && pct - lastPct >= 3) {
            lastPct = pct;
            env->CallVoidMethod(progressCb, onProgress, static_cast<jint>(pct));
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
    }

    gPool->waitAll();

    if (onProgress) {
        env->CallVoidMethod(progressCb, onProgress, static_cast<jint>(100));
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE; // ✅ Thông báo có thay đổi thật sự
}

// =============================================================
// 🧹 JNI helpers
// =============================================================
extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_clearCache(JNIEnv *, jclass) {
    s_lastHash.store(0ull, std::memory_order_relaxed);
    s_lastLutPath.clear();
}

extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_releasePool(JNIEnv *, jclass) {
    delete gPool;
    gPool = nullptr;
}
