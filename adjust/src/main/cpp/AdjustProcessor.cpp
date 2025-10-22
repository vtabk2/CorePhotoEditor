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
#include <fstream>

#include "adjust_common.h"

#include <android/log.h>

#define LOG_TAG "TAG5"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


// =============================================================
// 🎨 LUT 3D TABLE SUPPORT
// =============================================================
struct Lut3D {
    int size = 0;
    std::vector<float> data; // size^3 * 3
    bool valid() const { return size > 0 && data.size() == (size_t) size * size * size * 3; }
};

static bool loadTableFile(const std::string &path, Lut3D &lut) {
    std::ifstream f(path, std::ios::binary);
    if (!f.is_open()) return false;
    uint32_t header[2];
    f.read(reinterpret_cast<char *>(header), 8);
    lut.size = (int) header[0];
    if (lut.size <= 0) return false;
    size_t count = (size_t) lut.size * lut.size * lut.size * 3;
    lut.data.resize(count);
    f.read(reinterpret_cast<char *>(lut.data.data()), count * sizeof(float));
    return lut.valid();
}

static inline void sampleLUT(const Lut3D &lut, float r, float g, float b,
                             float &rr, float &gg, float &bb) {
    const int S = lut.size;
    if (S <= 1) {
        rr = r;
        gg = g;
        bb = b;
        return;
    }

    float rf = r * (S - 1);
    float gf = g * (S - 1);
    float bf = b * (S - 1);
    int r0 = (int) floorf(rf), g0 = (int) floorf(gf), b0 = (int) floorf(bf);
    int r1 = std::min(r0 + 1, S - 1);
    int g1 = std::min(g0 + 1, S - 1);
    int b1 = std::min(b0 + 1, S - 1);
    float wr = rf - r0, wg = gf - g0, wb = bf - b0;

    auto idx = [&](int rr, int gg, int bb) { return ((rr * S + gg) * S + bb) * 3; };
    auto lerp = [](float a, float b, float t) { return a + (b - a) * t; };

    float c000[3], c001[3], c010[3], c011[3], c100[3], c101[3], c110[3], c111[3];
    memcpy(c000, &lut.data[idx(r0, g0, b0)], 12);
    memcpy(c001, &lut.data[idx(r0, g0, b1)], 12);
    memcpy(c010, &lut.data[idx(r0, g1, b0)], 12);
    memcpy(c011, &lut.data[idx(r0, g1, b1)], 12);
    memcpy(c100, &lut.data[idx(r1, g0, b0)], 12);
    memcpy(c101, &lut.data[idx(r1, g0, b1)], 12);
    memcpy(c110, &lut.data[idx(r1, g1, b0)], 12);
    memcpy(c111, &lut.data[idx(r1, g1, b1)], 12);

    float c00[3], c01[3], c10[3], c11[3], c0[3], c1[3];
    for (int i = 0; i < 3; ++i) {
        c00[i] = lerp(c000[i], c001[i], wb);
        c01[i] = lerp(c010[i], c011[i], wb);
        c10[i] = lerp(c100[i], c101[i], wb);
        c11[i] = lerp(c110[i], c111[i], wb);
        c0[i] = lerp(c00[i], c01[i], wg);
        c1[i] = lerp(c10[i], c11[i], wg);
    }
    rr = lerp(c0[0], c1[0], wr);
    gg = lerp(c0[1], c1[1], wr);
    bb = lerp(c0[2], c1[2], wr);
}

// --- extern modules (đã có .cpp trong project) ---
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
            std::lock_guard <std::mutex> lock(mutex_);
            tasks_.push(std::move(task));
        }
        cv_.notify_one();
    }

    void waitAll() {
        std::unique_lock <std::mutex> lock(waitMutex_);
        waitCv_.wait(lock, [this] { return active_ == 0 && tasks_.empty(); });
    }

private:
    std::vector <std::thread> workers_;
    std::queue <std::function<void()>> tasks_;
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
                        std::unique_lock <std::mutex> lock(mutex_);
                        cv_.wait(lock, [this] { return stop_.load() || !tasks_.empty(); });
                        if (stop_.load() && tasks_.empty()) return;
                        task = std::move(tasks_.front());
                        tasks_.pop();
                        active_++;
                    }
                    task();
                    {
                        std::lock_guard <std::mutex> lk(waitMutex_);
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
        for (auto &t: workers_) if (t.joinable()) t.join();
        std::lock_guard <std::mutex> lock(mutex_);
        while (!tasks_.empty()) tasks_.pop();
    }
};

// =============================================================
// 🌍 Global Variables
// =============================================================
static ThreadPool *gPool = nullptr;
static std::atomic <uint64_t> s_lastHash{0ull};

// =============================================================
// ⚙️ Helpers: đọc dữ liệu từ AdjustParams.kt
// =============================================================
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
        jsize len = std::min((jsize) 8, env->GetArrayLength(hueArr));
        env->GetFloatArrayRegion(hueArr, 0, len, p.hslHue);
    }
    if (satArr) {
        jsize len = std::min((jsize) 8, env->GetArrayLength(satArr));
        env->GetFloatArrayRegion(satArr, 0, len, p.hslSaturation);
    }
    if (lumArr) {
        jsize len = std::min((jsize) 8, env->GetArrayLength(lumArr));
        env->GetFloatArrayRegion(lumArr, 0, len, p.hslLuminance);
    }

    // Active mask
    p.activeMask = getLongField(env, paramsObj, "activeMask");
}

// =============================================================
// 🔢 computeAdjustHash
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
    return h;
}

// =============================================================
// 🧮 Process one range
// =============================================================
static void
processRange(void *basePixels, int start, int end, int width, int height, int strideBytes,
             const AdjustParams &p, std::atomic<int> &doneCounter) {
    uint8_t *base = reinterpret_cast<uint8_t *>(basePixels);
    for (int idx = start; idx < end; ++idx) {
        int y = idx / width;
        int x = idx % width;
        uint32_t *row = reinterpret_cast<uint32_t *>(base + y * strideBytes);
        uint32_t color = row[x];

        float a = float((color >> 24) & 0xFF);
        float r = float((color >> 16) & 0xFF);
        float g = float((color >> 8) & 0xFF);
        float b = float(color & 0xFF);

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
            applyDetailAdjust(rf, gf, bf, float(x), float(y), float(width), float(height), p);
        if (p.activeMask & MASK_VIGNETTE)
            applyVignetteAt(rf, gf, bf, float(x), float(y), float(width), float(height), p);
        if (p.activeMask & MASK_GRAIN) applyGrainAt(rf, gf, bf, p);

        rf = std::clamp(rf, 0.0f, 1.0f);
        gf = std::clamp(gf, 0.0f, 1.0f);
        bf = std::clamp(bf, 0.0f, 1.0f);

        row[x] = (uint32_t(a) << 24)
                 | (uint32_t(uint8_t(rf * 255)) << 16)
                 | (uint32_t(uint8_t(gf * 255)) << 8)
                 | uint32_t(uint8_t(bf * 255));

        ++doneCounter;
    }
}

// =============================================================
// 🔗 JNI: applyAdjustNative
// =============================================================
extern "C"
JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_applyAdjustNative(JNIEnv
*env, jobject,
jobject bitmap,
        jobject
paramsObj,
jobject progressCb
) {
if (!bitmap || !paramsObj) return;

// init pool
if (!gPool) {
unsigned int hw = std::thread::hardware_concurrency();
unsigned int n = std::max(2u, (hw > 0 ? hw / 2 : 2u));
gPool = new ThreadPool(n);
}

AdjustParams p{};
loadParamsFromJava(env, paramsObj, p
);

uint64_t hash = computeAdjustHash(p);
uint64_t last = s_lastHash.load(std::memory_order_relaxed);
if (hash == last) return;
s_lastHash.
store(hash, std::memory_order_relaxed
);

AndroidBitmapInfo info;
if (
AndroidBitmap_getInfo(env, bitmap, &info
) != ANDROID_BITMAP_RESULT_SUCCESS) return;
if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;

void *pixels = nullptr;
if (
AndroidBitmap_lockPixels(env, bitmap, &pixels
) != ANDROID_BITMAP_RESULT_SUCCESS) return;

if (p.
activeMask &MASK_LUT
){
// --- LẤY LUT PATH TỪ PARAMS ---
std::string lutPath;
{
jclass cls = env->GetObjectClass(paramsObj);
jfieldID lutField = env->GetFieldID(cls, "lutPath", "Ljava/lang/String;");
if (lutField) {
jstring jstr = (jstring) env->GetObjectField(paramsObj, lutField);
if (jstr) {
const char *cstr = env->GetStringUTFChars(jstr, nullptr);
if (cstr)
lutPath = cstr;
env->
ReleaseStringUTFChars(jstr, cstr
);
}
}
}
LOGI("LUT flag active — path: %s", lutPath.c_str());
// --- ÁP LUT TRƯỚC KHI ADJUST ---
if (!lutPath.

empty()

) {
LOGI("Applying LUT from path: %s", lutPath.c_str());
Lut3D lut;
if (
loadTableFile(lutPath, lut
)) {
uint8_t *base = reinterpret_cast<uint8_t *>(pixels);
for (
int y = 0;
y<info.
height;
++y) {
uint32_t *row = reinterpret_cast<uint32_t *>(base + y * info.stride);
for (
int x = 0;
x<info.
width;
++x) {
uint32_t c = row[x];
uint8_t a = (c >> 24) & 0xFF;
float r = ((c >> 16) & 0xFF) / 255.f;
float g = ((c >> 8) & 0xFF) / 255.f;
float b = (c & 0xFF) / 255.f;

float rr, gg, bb;
sampleLUT(lut, r, g, b, rr, gg, bb
);

rr = std::min(1.f, std::max(0.f, rr));
gg = std::min(1.f, std::max(0.f, gg));
bb = std::min(1.f, std::max(0.f, bb));

row[x] = (
uint32_t(a)
<< 24)
| (
uint32_t(rr
* 255) << 16)
| (
uint32_t(gg
* 255) << 8)
|
uint32_t(bb
* 255);
}
}
LOGI("LUT applied successfully");
} else {
LOGE("Failed to load LUT file: %s", lutPath.c_str());
}
} else {
LOGI("No LUT path provided — skipping LUT stage");
}
}

const int W = info.width;
const int H = info.height;
const int total = W * H;
const int stride = info.stride;

jmethodID onProgress = nullptr;
if (progressCb) {
jclass cbCls = env->GetObjectClass(progressCb);
onProgress = cbCls ? env->GetMethodID(cbCls, "onProgress", "(I)V") : nullptr;
}

std::atomic<int> doneCounter{0};
unsigned int nThreads = std::max(1u, std::thread::hardware_concurrency());
int chunk = (total + nThreads - 1) / nThreads;

for (
unsigned int t = 0;
t<nThreads;
++t) {
int start = int(t * chunk);
int end = std::min(total, start + chunk);
if (start >= end) break;

gPool->enqueue([p, pixels, W, H, stride, start, end, &doneCounter]() {
processRange(pixels, start, end, W, H, stride, p, doneCounter
);
});
}

// progress polling
int lastPct = 0;
while (doneCounter.

load()

< total) {
std::this_thread::sleep_for(std::chrono::milliseconds(6)
);
int pct = int(doneCounter.load() * 100 / total);
if (
onProgress &&pct
> lastPct) {
lastPct = pct;
env->
CallVoidMethod(progressCb, onProgress, pct
);
if (env->

ExceptionCheck()

) env->

ExceptionClear();

}
}

gPool->

waitAll();

if (onProgress) {
env->
CallVoidMethod(progressCb, onProgress,
100);
if (env->

ExceptionCheck()

) env->

ExceptionClear();

}

AndroidBitmap_unlockPixels(env, bitmap
);
}

// =============================================================
// 🧹 JNI: clearCache + releasePool
// =============================================================
extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_clearCache(JNIEnv
*, jclass) {
s_lastHash.store(0ull, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_core_adjust_AdjustProcessor_releasePool(JNIEnv
*, jclass) {
delete
gPool;
gPool = nullptr;
}
