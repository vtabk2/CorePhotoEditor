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
#include <sys/stat.h>

#include "adjust_common.h"

#define LOG_TAG "TAG5"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// =============================================================
// 🎨 LUT 3D TABLE SUPPORT
// =============================================================
struct Lut3D {
    int32_t size = 0;
    std::vector<float> data; // size^3 * 3
    bool valid() const {
        const size_t need =
                static_cast<size_t>(size) * static_cast<size_t>(size) * static_cast<size_t>(size) *
                3u;
        return size > 0 && data.size() == need;
    }
};

static bool loadTableFile(const std::string &path, Lut3D &lut) {
    std::ifstream f(path, std::ios::binary);
    if (!f.is_open()) {
        LOGE("Failed to open LUT file: %s", path.c_str());
        return false;
    }
    uint32_t header[2] = {0u, 0u};
    f.read(reinterpret_cast<char *>(header), sizeof(header));
    if (!f) {
        LOGE("Failed to read LUT header: %s", path.c_str());
        return false;
    }
    lut.size = static_cast<int32_t>(header[0]);
    if (lut.size <= 0) {
        LOGE("Invalid LUT size: %d", lut.size);
        return false;
    }
    const size_t count = static_cast<size_t>(lut.size) * static_cast<size_t>(lut.size) *
                         static_cast<size_t>(lut.size) * 3u;
    lut.data.resize(count);
    f.read(reinterpret_cast<char *>(lut.data.data()), count * sizeof(float));
    if (!f) {
        LOGE("Failed to read LUT data: %s", path.c_str());
        return false;
    }
    if (lut.valid()) {
        LOGI("Loaded LUT (size=%d) from %s", lut.size, path.c_str());
        return true;
    }
    LOGE("Invalid LUT content: %s", path.c_str());
    return false;
}

static inline void sampleLUT(const Lut3D &lut, float r, float g, float b,
                             float &rr, float &gg, float &bb) {
    const int32_t S = lut.size;
    if (S <= 1) {
        rr = r;
        gg = g;
        bb = b;
        return;
    }

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
        return (static_cast<size_t>(ir) * static_cast<size_t>(S) + static_cast<size_t>(ig)) *
               static_cast<size_t>(S) + static_cast<size_t>(ib);
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

extern void
applyDetailAdjust(float &rf, float &gf, float &bf, float x, float y, float width, float height,
                  const AdjustParams &p);

extern "C" void applyVignetteAt(float &rf, float &gf, float &bf, float x, float y, float w, float h,
                                const AdjustParams &p);
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
// 🌍 Global Variables
// =============================================================
static ThreadPool *gPool = nullptr;
static std::atomic<uint64_t> s_lastHash{0ull};
static std::string s_lastLutPath;

// =============================================================
// ⚙️ Helpers: read fields from AdjustParams Java object
// =============================================================
static inline void DeleteLocalRefSafely(JNIEnv *env, jobject obj) {
    if (obj) env->DeleteLocalRef(obj);
}

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
    p.exposure = getFieldF(env, paramsObj, "exposure");
    p.brightness = getFieldF(env, paramsObj, "brightness");
    p.contrast = getFieldF(env, paramsObj, "contrast");
    p.highlights = getFieldF(env, paramsObj, "highlights");
    p.shadows = getFieldF(env, paramsObj, "shadows");
    p.whites = getFieldF(env, paramsObj, "whites");
    p.blacks = getFieldF(env, paramsObj, "blacks");

    // Color
    p.temperature = getFieldF(env, paramsObj, "temperature");
    p.tint = getFieldF(env, paramsObj, "tint");
    p.vibrance = getFieldF(env, paramsObj, "vibrance");
    p.saturation = getFieldF(env, paramsObj, "saturation");

    // Detail
    p.texture = getFieldF(env, paramsObj, "texture");
    p.clarity = getFieldF(env, paramsObj, "clarity");
    p.dehaze = getFieldF(env, paramsObj, "dehaze");

    // Effects
    p.vignette = getFieldF(env, paramsObj, "vignette");
    p.grain = getFieldF(env, paramsObj, "grain");

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

    // Clamp input defensively
    p.vignette = clampf(p.vignette, 0.f, 1.f);
    p.grain = std::max(0.f, p.grain);
}

// =============================================================
// 🧮 computeAdjustHash (to avoid reprocessing identical params)
// =============================================================
static inline uint64_t bitsOfFloat(float f) {
    union {
        float f;
        uint32_t u;
    } x{f};
    return static_cast<uint64_t>(x.u);
}

static uint64_t computeAdjustHash(const AdjustParams &p) {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v) {
        h ^= v;
        h *= 1099511628211ull;
    };

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

    // ✅ Gộp LUT vào hash nếu có bật MASK_LUT
    if ((p.activeMask & MASK_LUT) && !p.lutPath.empty()) {
        // Cách 1: hash chuỗi đường dẫn (nhanh, đủ tốt)
        for (unsigned char c: p.lutPath) mix((uint64_t) c);

        // (Tuỳ chọn) Cách 2: thêm hash metadata file để thay file cùng tên vẫn khác hash
        struct stat st{};
        if (stat(p.lutPath.c_str(), &st) == 0) {
            mix((uint64_t) st.st_size);
            mix((uint64_t) st.st_mtime);
        }
    }
    return h;
}

// =============================================================
// 🧮 processRange
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
        const uint8_t gu = static_cast<uint8_t>((color >> 8) & 0xFFu);
        const uint8_t bu = static_cast<uint8_t>( color & 0xFFu);

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
        if (p.activeMask & MASK_HSL) applyHSLAdjust(r, g, b, p);

        r = std::clamp(r, 0.0f, 255.0f);
        g = std::clamp(g, 0.0f, 255.0f);
        b = std::clamp(b, 0.0f, 255.0f);

        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        if (p.activeMask & MASK_COLOR) applyColorAdjust(rf, gf, bf, p);
        if (p.activeMask & MASK_DETAIL)
            applyDetailAdjust(rf, gf, bf, static_cast<float>(x), static_cast<float>(y),
                              static_cast<float>(width), static_cast<float>(height), p);
        if (p.activeMask & MASK_VIGNETTE)
            applyVignetteAt(rf, gf, bf, static_cast<float>(x), static_cast<float>(y),
                            static_cast<float>(width), static_cast<float>(height), p);
        if (p.activeMask & MASK_GRAIN) applyGrainAt(rf, gf, bf, p);

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
                 | (static_cast<uint32_t>(static_cast<uint8_t>(gout)) << 8)
                 | static_cast<uint32_t>(static_cast<uint8_t>(bout));

        doneCounter.fetch_add(1, std::memory_order_relaxed);
    }
}

// helper
static inline bool isZero8(const float a[8]) {
    for (int i = 0; i < 8; ++i) if (a[i] != 0.0f) return false;
    return true;
}

static bool isNoOp(const AdjustParams &p, bool hasLut) {
    // Không có mask nào bật
    if (p.activeMask == 0) return true;

    // LIGHT
    if (p.activeMask & MASK_LIGHT) {
        if (p.exposure != 0 || p.brightness != 0 || p.contrast != 0 ||
            p.highlights != 0 || p.shadows != 0 || p.whites != 0 || p.blacks != 0)
            return false;
    }
    // COLOR
    if (p.activeMask & MASK_COLOR) {
        if (p.temperature != 0 || p.tint != 0 || p.vibrance != 0 || p.saturation != 0)
            return false;
    }
    // DETAIL
    if (p.activeMask & MASK_DETAIL) {
        if (p.texture != 0 || p.clarity != 0 || p.dehaze != 0)
            return false;
    }
    // HSL
    if (p.activeMask & MASK_HSL) {
        if (!isZero8(p.hslHue) || !isZero8(p.hslSaturation) || !isZero8(p.hslLuminance))
            return false;
    }
    // Vignette/Grain
    if ((p.activeMask & MASK_VIGNETTE) && p.vignette != 0) return false;
    if ((p.activeMask & MASK_GRAIN) && p.grain != 0) return false;

    // LUT
    if (p.activeMask & MASK_LUT) {
        if (hasLut) return false; // có LUT path hợp lệ thì KHÔNG phải no-op
    }

    return true; // mọi thứ đều “0” hoặc mask không bật
}

// =============================================================
// 🔗 JNI: applyAdjustNative
// =============================================================
extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(JNIEnv *env, jobject /*thiz*/,
                                                       jobject bitmap,
                                                       jobject paramsObj, jobject progressCb) {
    if (!bitmap || !paramsObj) return;

    // Init thread pool
    if (!gPool) {
        const unsigned int hw = std::thread::hardware_concurrency();
        const unsigned int n = std::max(2u, (hw > 0 ? hw / 2 : 2u));
        gPool = new ThreadPool(static_cast<size_t>(n));
    }

    // Load params
    AdjustParams p{};
    loadParamsFromJava(env, paramsObj, p);

    // Prepare progress method early (so we can signal on skip)
    jmethodID onProgress = nullptr;
    if (progressCb) {
        jclass cbCls = env->GetObjectClass(progressCb);
        if (cbCls) onProgress = env->GetMethodID(cbCls, "onProgress", "(I)V");
        DeleteLocalRefSafely(env, cbCls);
    }

    // Skip if hash identical (notify 100%)
    const uint64_t hash = computeAdjustHash(p);
    const uint64_t last = s_lastHash.load(std::memory_order_relaxed);
    if (hash == last) { // original early-return path
        if (onProgress) {
            env->CallVoidMethod(progressCb, onProgress, static_cast<jint>(100));
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        return;
    }
    s_lastHash.store(hash, std::memory_order_relaxed);

    // Read LUT path from paramsObj.lutPath
    std::string lutPath;
    {
        jclass cls = env->GetObjectClass(paramsObj);
        jfieldID lutField = cls ? env->GetFieldID(cls, "lutPath", "Ljava/lang/String;") : nullptr;
        if (lutField) {
            jstring jstr = static_cast<jstring>(env->GetObjectField(paramsObj, lutField));
            if (jstr) {
                const char *cstr = env->GetStringUTFChars(jstr, nullptr);
                if (cstr) {
                    lutPath.assign(cstr);
                    env->ReleaseStringUTFChars(jstr, cstr);
                }
                DeleteLocalRefSafely(env, jstr);
            }
        }
        DeleteLocalRefSafely(env, cls);
    }

    const bool hasLut = ((p.activeMask & MASK_LUT) && !lutPath.empty());
    if (isNoOp(p, hasLut)) {
        LOGI("🟢 No-op detected (all params 0, no LUT) — skip adjust.");
        return;
    }

    const bool onlyLut = (p.activeMask == MASK_LUT);
    if (onlyLut && !lutPath.empty() && lutPath == s_lastLutPath) {
        return;
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return;

    // Make sure we always unlock on every path
    auto unlockPixels = [&]() {
        AndroidBitmap_unlockPixels(env, bitmap);
    };

    const bool premultiplied = (info.flags & ANDROID_BITMAP_FLAGS_ALPHA_PREMUL) != 0;

    // ---------------------------------------------------------
    // LUT Stage (apply BEFORE other adjusts) if path provided
    // ---------------------------------------------------------
    if ((p.activeMask & MASK_LUT) && !lutPath.empty()) {
        bool sameLut = (lutPath == s_lastLutPath);
        if (sameLut) {
            LOGI("🎨 LUT unchanged (%s) — skip LUT reapply", lutPath.c_str());
        } else {
            LOGI("🎨 Applying LUT from path: %s", lutPath.c_str());
            Lut3D lut;
            if (loadTableFile(lutPath, lut)) {
                s_lastLutPath = lutPath; // remember last LUT path

                uint8_t *base = reinterpret_cast<uint8_t *>(pixels);
                for (int32_t y = 0; y < static_cast<int32_t>(info.height); ++y) {
                    auto *row = reinterpret_cast<uint32_t *>(base + static_cast<size_t>(y) *
                                                                    static_cast<size_t>(info.stride));
                    for (int32_t x = 0; x < static_cast<int32_t>(info.width); ++x) {
                        const uint32_t c = row[x];
                        const uint8_t a = static_cast<uint8_t>((c >> 24) & 0xFFu);
                        float r = static_cast<float>((c >> 16) & 0xFFu) / 255.0f;
                        float g = static_cast<float>((c >> 8) & 0xFFu) / 255.0f;
                        float b = static_cast<float>( c & 0xFFu) / 255.0f;

                        if (premultiplied && a > 0u) {
                            const float af = static_cast<float>(a) / 255.0f;
                            const float inv = (af > 0.0f ? (1.0f / af) : 0.0f);
                            r = std::min(1.0f, r * inv);
                            g = std::min(1.0f, g * inv);
                            b = std::min(1.0f, b * inv);
                        }

                        float rr, gg, bb;
                        sampleLUT(lut, r, g, b, rr, gg, bb);

                        rr = std::clamp(rr, 0.0f, 1.0f);
                        gg = std::clamp(gg, 0.0f, 1.0f);
                        bb = std::clamp(bb, 0.0f, 1.0f);

                        if (premultiplied && a > 0u) {
                            const float af = static_cast<float>(a) / 255.0f;
                            rr = std::clamp(rr * af, 0.0f, 1.0f);
                            gg = std::clamp(gg * af, 0.0f, 1.0f);
                            bb = std::clamp(bb * af, 0.0f, 1.0f);
                        }

                        row[x] = (static_cast<uint32_t>(a) << 24)
                                 | (static_cast<uint32_t>(static_cast<uint8_t>(rr * 255.0f)) << 16)
                                 | (static_cast<uint32_t>(static_cast<uint8_t>(gg * 255.0f)) << 8)
                                 | static_cast<uint32_t>(static_cast<uint8_t>(bb * 255.0f));
                    }
                }
                LOGI("✅ LUT applied successfully");
            } else {
                LOGE("❌ Failed to load LUT file: %s", lutPath.c_str());
            }
        }
    } else {
        LOGI("⚠️ No LUT mask active or LUT path empty — skipping LUT stage");
    }

    // ---------- APPLY ADJUSTS (multi-threaded) ----------
    const int32_t W = static_cast<int32_t>(info.width);
    const int32_t H = static_cast<int32_t>(info.height);
    const int64_t total = static_cast<int64_t>(W) * static_cast<int64_t>(H);
    const size_t stride = static_cast<size_t>(info.stride);

    std::atomic<int64_t> doneCounter{0};
    const unsigned int nThreads = std::max(1u, std::thread::hardware_concurrency());
    const int64_t chunk =
            (total + static_cast<int64_t>(nThreads) - 1) / static_cast<int64_t>(nThreads);

    for (unsigned int t = 0; t < nThreads; ++t) {
        const int64_t start = static_cast<int64_t>(t) * chunk;
        const int64_t end = std::min<int64_t>(total, start + chunk);
        if (start >= end) break;

        gPool->enqueue([p, pixels, premultiplied, start, end, W, H, stride, &doneCounter]() {
            processRange(pixels, start, end, W, H, stride, p, doneCounter, premultiplied);
        });

    }

    // Progress polling
    int32_t lastPct = 0;
    while (doneCounter.load(std::memory_order_relaxed) < total) {
        std::this_thread::sleep_for(std::chrono::milliseconds(6));
        const int64_t done = doneCounter.load(std::memory_order_relaxed);
        const int32_t pct = static_cast<int32_t>((done * 100) / std::max<int64_t>(total, 1));
        if (onProgress && pct > lastPct) {
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

    unlockPixels();
}

// =============================================================
// 🧹 JNI helpers
// =============================================================
extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_clearCache(JNIEnv *, jclass) {
    s_lastHash.store(0ull, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_releasePool(JNIEnv *, jclass) {
    delete gPool;
    gPool = nullptr;
}
