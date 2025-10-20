#include <jni.h>
#include <android/bitmap.h>
#include <vector>
#include <thread>
#include <queue>
#include <condition_variable>
#include <atomic>
#include <functional>
#include <cmath>
#include <algorithm>
#include <chrono>

#include "adjust_common.h"

extern void applyLightAdjust(float &r, float &g, float &b, const AdjustParams &p);
extern "C" void applyHSLAdjust(float &r, float &g, float &b, const AdjustParams &p);
extern void applyColorAdjust(float &rf, float &gf, float &bf, const AdjustParams &p);
extern void applyDetailAdjust(float &rf, float &gf, float &bf, float x, float y, float width, float height, const AdjustParams &p);
extern "C" void applyVignetteAt(float &rf, float &gf, float &bf, float x, float y, float w, float h, const AdjustParams &p);
extern "C" void applyGrainAt(float &rf, float &gf, float &bf, const AdjustParams &p);

// ---------------- ThreadPool ----------------
class ThreadPool {
public:
    ThreadPool(size_t n) { start(n); }
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

    std::atomic<int> active_{0};
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
                        active_++;
                    }
                    // execute
                    task();
                    {
                        std::lock_guard<std::mutex> lk(waitMutex_);
                        active_--;
                        if (active_ == 0 && tasks_.empty()) waitCv_.notify_all();
                    }
                }
            });
        }
    }

    void stopAll() {
        stop_.store(true);
        cv_.notify_all();
        for (auto &t : workers_) if (t.joinable()) t.join();
        // clear tasks
        std::lock_guard<std::mutex> lock(mutex_);
        while (!tasks_.empty()) tasks_.pop();
    }
};

// ---------------- Globals ----------------
static ThreadPool *gPool = nullptr;
static std::atomic<uint64_t> s_lastHash{0ull};

// ---------------- Helpers: read AdjustParams from Java ----------------
static float getFieldF(JNIEnv *env, jobject obj, const char *name) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, name, "F");
    return fid ? env->GetFloatField(obj, fid) : 0.0f;
}

static jfloatArray getFloatArray(JNIEnv *env, jobject obj, const char *name) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, name, "[F");
    if (!fid) return nullptr;
    return static_cast<jfloatArray>(env->GetObjectField(obj, fid));
}

static uint64_t getLongField(JNIEnv *env, jobject obj, const char *name) {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, name, "J");
    return fid ? static_cast<uint64_t>(env->GetLongField(obj, fid)) : 0ull;
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

    // HSL arrays (8)
    jfloatArray hueArr = getFloatArray(env, paramsObj, "hslHue");
    jfloatArray satArr = getFloatArray(env, paramsObj, "hslSaturation");
    jfloatArray lumArr = getFloatArray(env, paramsObj, "hslLuminance");
    if (hueArr) {
        jsize len = env->GetArrayLength(hueArr);
        len = std::min((jsize)8, len);
        env->GetFloatArrayRegion(hueArr, 0, len, p.hslHue);
    }
    if (satArr) {
        jsize len = env->GetArrayLength(satArr);
        len = std::min((jsize)8, len);
        env->GetFloatArrayRegion(satArr, 0, len, p.hslSaturation);
    }
    if (lumArr) {
        jsize len = env->GetArrayLength(lumArr);
        len = std::min((jsize)8, len);
        env->GetFloatArrayRegion(lumArr, 0, len, p.hslLuminance);
    }

    // activeMask (long)
    p.activeMask = getLongField(env, paramsObj, "activeMask");
}

// ---------------- Hash (FNV-like using bits of floats + arrays) ----------------
static inline uint64_t bitsOfFloat(float f) {
    union { float f; uint32_t u; } x{f};
    return static_cast<uint64_t>(x.u);
}
static uint64_t computeAdjustHash(const AdjustParams &p) {
    uint64_t h = 1469598103934665603ull;
    auto mix = [&](uint64_t v){ h ^= v; h *= 1099511628211ull; };

    // scalars
    mix(bitsOfFloat(p.exposure)); mix(bitsOfFloat(p.brightness)); mix(bitsOfFloat(p.contrast));
    mix(bitsOfFloat(p.highlights)); mix(bitsOfFloat(p.shadows)); mix(bitsOfFloat(p.whites)); mix(bitsOfFloat(p.blacks));
    mix(bitsOfFloat(p.temperature)); mix(bitsOfFloat(p.tint)); mix(bitsOfFloat(p.saturation)); mix(bitsOfFloat(p.vibrance));
    mix(bitsOfFloat(p.texture)); mix(bitsOfFloat(p.clarity)); mix(bitsOfFloat(p.dehaze));
    mix(bitsOfFloat(p.vignette)); mix(bitsOfFloat(p.grain));
    // arrays
    for (int i = 0; i < 8; ++i) {
        mix(bitsOfFloat(p.hslHue[i]));
        mix(bitsOfFloat(p.hslSaturation[i]));
        mix(bitsOfFloat(p.hslLuminance[i]));
    }
    mix(p.activeMask);
    return h;
}

// ---------------- Process one range (worker thread) ----------------
// Note: worker threads MUST NOT call JNI env methods (we avoid that).
static void processRange(void* basePixels, int start, int end, int width, int height, int strideBytes,
                         const AdjustParams &p, std::atomic<int> &doneCounter) {
    uint8_t* base = reinterpret_cast<uint8_t*>(basePixels);
    for (int idx = start; idx < end; ++idx) {
        int y = idx / width;
        int x = idx % width;
        uint32_t* row = reinterpret_cast<uint32_t*>(base + y * strideBytes);
        uint32_t color = row[x];

        float a = float((color >> 24) & 0xFF);
        float r = float((color >> 16) & 0xFF);
        float g = float((color >> 8) & 0xFF);
        float b = float(color & 0xFF);

        // Light (operate in 0..255 space as your adjust_light expects)
        if (p.activeMask & MASK_LIGHT) applyLightAdjust(r, g, b, p);

        // HSL (this function expects 0..255 and does linearization internally)
        if (p.activeMask & MASK_HSL) applyHSLAdjust(r, g, b, p);

        // clamp to 0..255
        r = std::clamp(r, 0.0f, 255.0f);
        g = std::clamp(g, 0.0f, 255.0f);
        b = std::clamp(b, 0.0f, 255.0f);

        // convert to 0..1 for color/detail/vignette/grain modules
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        if (p.activeMask & MASK_COLOR) applyColorAdjust(rf, gf, bf, p);
        if (p.activeMask & MASK_DETAIL) applyDetailAdjust(rf, gf, bf, float(x), float(y), float(width), float(height), p);
        if (p.activeMask & MASK_VIGNETTE) applyVignetteAt(rf, gf, bf, float(x), float(y), float(width), float(height), p);
        if (p.activeMask & MASK_GRAIN) applyGrainAt(rf, gf, bf, p);

        // clamp to 0..1 and write back as packed RGBA8888
        rf = std::clamp(rf, 0.0f, 1.0f);
        gf = std::clamp(gf, 0.0f, 1.0f);
        bf = std::clamp(bf, 0.0f, 1.0f);

        uint32_t out = ( (uint32_t(uint8_t(a)) << 24) |
                         (uint32_t(uint8_t(rf * 255.0f)) << 16) |
                         (uint32_t(uint8_t(gf * 255.0f)) << 8) |
                         (uint32_t(uint8_t(bf * 255.0f))) );
        row[x] = out;

        ++doneCounter;
    }
}

// ---------------- JNI: applyAdjustNative (Bitmap direct) ----------------
extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(JNIEnv *env, jobject thiz,
                                                       jobject bitmap, jobject paramsObj, jobject progressCb) {
    if (!bitmap || !paramsObj) return;

    // init pool if needed
    if (!gPool) {
        unsigned int hw = std::thread::hardware_concurrency();
        unsigned int poolSize = std::max<unsigned int>(2, (hw > 0 ? hw/2 : 2));
        gPool = new ThreadPool(poolSize);
    }

    // load params
    AdjustParams p{};
    loadParamsFromJava(env, paramsObj, p);

    uint64_t hash = computeAdjustHash(p);
    uint64_t last = s_lastHash.load(std::memory_order_relaxed);
    if (hash == last) {
        // still call progress 100 if callback present
        if (progressCb) {
            jclass cbCls = env->GetObjectClass(progressCb);
            jmethodID onP = cbCls ? env->GetMethodID(cbCls, "onProgress", "(I)V") : nullptr;
            if (onP) env->CallVoidMethod(progressCb, onP, 100);
        }
        return;
    }
    s_lastHash.store(hash, std::memory_order_relaxed);

    // bitmap info
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return;

    const int W = (int) info.width;
    const int H = (int) info.height;
    const int total = W * H;
    const int stride = (int) info.stride;

    // prepare progress callback method id (call from THIS thread only)
    jmethodID onProgress = nullptr;
    if (progressCb) {
        jclass cbCls = env->GetObjectClass(progressCb);
        onProgress = cbCls ? env->GetMethodID(cbCls, "onProgress", "(I)V") : nullptr;
        if (onProgress) env->CallVoidMethod(progressCb, onProgress, 0); // start 0%
        if (env->ExceptionCheck()) { env->ExceptionClear(); onProgress = nullptr; }
    }

    // if no active mask -> skip quickly
    if (p.activeMask == 0ull) {
        if (onProgress) env->CallVoidMethod(progressCb, onProgress, 100);
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    // split work
    unsigned int nThreads = std::max<unsigned int>(1, std::thread::hardware_concurrency());
    int chunk = (total + nThreads - 1) / nThreads;

    std::atomic<int> doneCounter{0};

    for (unsigned int t = 0; t < nThreads; ++t) {
        int start = int(t * chunk);
        int end = std::min(total, start + chunk);
        if (start >= end) break;

        gPool->enqueue([p, pixels, W, H, stride, start, end, &doneCounter]() {
            processRange(pixels, start, end, W, H, stride, p, doneCounter);
        });
    }

    // MAIN thread: poll progress and call JNI callback (safe)
    int lastPct = 0;
    while (true) {
        // wait a little to reduce jitter
        std::this_thread::sleep_for(std::chrono::milliseconds(6));
        int done = doneCounter.load(std::memory_order_relaxed);
        int pct = (int)((done * 100) / (total > 0 ? total : 1));
        if (pct > lastPct && onProgress) {
            lastPct = pct;
            env->CallVoidMethod(progressCb, onProgress, pct);
            if (env->ExceptionCheck()) { env->ExceptionClear(); onProgress = nullptr; }
        }
        // break when all tasks done
        // We cannot rely only on doneCounter==total because tasks might still be in queue;
        // but threadPool.waitAll ensures tasks finished — call it once then break.
        if (done >= total) break;
    }

    // ensure all worker tasks finished
    gPool->waitAll();

    // final callback 100%
    if (onProgress) env->CallVoidMethod(progressCb, onProgress, 100);
    if (env->ExceptionCheck()) env->ExceptionClear();

    AndroidBitmap_unlockPixels(env, bitmap);
}

// ---------------- JNI: clearCache + releasePool ----------------
extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_clearCache(JNIEnv *, jclass) {
    s_lastHash.store(0ull, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_releasePool(JNIEnv *, jclass) {
    if (gPool) {
        delete gPool;
        gPool = nullptr;
    }
}
