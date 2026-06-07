#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── Handle ────────────────────────────────────────────────────────────────────

struct LlamaHandle {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
};

static std::string j2s(JNIEnv* env, jstring js) {
    if (!js) return "";
    const char* c = env->GetStringUTFChars(js, nullptr);
    std::string s(c);
    env->ReleaseStringUTFChars(js, c);
    return s;
}

extern "C" {

// ── Init ──────────────────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_com_personai_app_core_LlamaEngine_nativeInit(
    JNIEnv* env, jobject,
    jstring jModelPath, jint contextSize, jint threads, jint gpuLayers)
{
    llama_backend_init();

    std::string path = j2s(env, jModelPath);
    LOGI("Loading model: %s  ctx=%d  threads=%d", path.c_str(), contextSize, threads);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpuLayers;

    llama_model* model = llama_load_model_from_file(path.c_str(), mparams);
    if (!model) { LOGE("Failed to load model"); return 0; }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = (uint32_t)contextSize;
    cparams.n_threads        = threads;
    cparams.n_threads_batch  = threads;
    cparams.flash_attn       = true;

    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_free_model(model);
        return 0;
    }

    auto* h = new LlamaHandle{model, ctx};
    LOGI("Model ready. Vocab: %d", llama_n_vocab(model));
    return reinterpret_cast<jlong>(h);
}

// ── Infer ─────────────────────────────────────────────────────────────────────

JNIEXPORT jstring JNICALL
Java_com_personai_app_core_LlamaEngine_nativeInfer(
    JNIEnv* env, jobject,
    jlong jhandle, jstring jPrompt,
    jfloat temp, jfloat topP, jint topK,
    jint maxTokens, jfloat repeatPenalty)
{
    auto* h = reinterpret_cast<LlamaHandle*>(jhandle);
    if (!h || !h->model || !h->ctx) return env->NewStringUTF("[null handle]");

    std::string prompt = j2s(env, jPrompt);

    // Tokenize
    std::vector<llama_token> tokens(prompt.size() + 32);
    int n = llama_tokenize(h->model, prompt.c_str(), (int)prompt.size(),
                           tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) {
        // Retry with larger buffer
        tokens.resize(-n + 32);
        n = llama_tokenize(h->model, prompt.c_str(), (int)prompt.size(),
                           tokens.data(), (int)tokens.size(), true, true);
    }
    if (n <= 0) { LOGE("Tokenize failed: %d", n); return env->NewStringUTF("[tokenize error]"); }
    tokens.resize(n);

    // Evaluate prompt
    llama_kv_cache_clear(h->ctx);
    {
        const int batch_max = 512;
        for (int i = 0; i < n; i += batch_max) {
            int len   = std::min(batch_max, n - i);
            auto batch = llama_batch_get_one(tokens.data() + i, len);
            if (llama_decode(h->ctx, batch) != 0) {
                LOGE("Prompt decode failed at %d", i);
                return env->NewStringUTF("[decode error]");
            }
        }
    }

    // Sampler chain
    auto sparams    = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (repeatPenalty > 1.0f)
        llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
    if (temp > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    // Generate
    std::string result;
    result.reserve(512);
    const llama_token eos = llama_token_eos(h->model);

    for (int i = 0; i < maxTokens; i++) {
        llama_token tok = llama_sampler_sample(smpl, h->ctx, -1);
        llama_sampler_accept(smpl, tok);

        if (tok == eos || llama_token_is_eog(h->model, tok)) break;

        char buf[256] = {};
        int len = llama_token_to_piece(h->ctx, tok, buf, sizeof(buf) - 1, 0, false);
        if (len > 0) result.append(buf, len);

        auto next = llama_batch_get_one(&tok, 1);
        if (llama_decode(h->ctx, next) != 0) break;
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

// ── State save / load ─────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_personai_app_core_LlamaEngine_nativeSaveState(
    JNIEnv* env, jobject, jlong jhandle, jstring jPath)
{
    auto* h = reinterpret_cast<LlamaHandle*>(jhandle);
    if (!h) return JNI_FALSE;
    std::string p = j2s(env, jPath);
    bool ok = llama_state_save_file(h->ctx, p.c_str(), nullptr, 0);
    LOGI("Save state → %s : %s", p.c_str(), ok ? "OK" : "FAIL");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_personai_app_core_LlamaEngine_nativeLoadState(
    JNIEnv* env, jobject, jlong jhandle, jstring jPath)
{
    auto* h = reinterpret_cast<LlamaHandle*>(jhandle);
    if (!h) return JNI_FALSE;
    std::string p = j2s(env, jPath);
    size_t n = 0;
    size_t bytes = llama_state_load_file(h->ctx, p.c_str(), nullptr, 0, &n);
    LOGI("Load state ← %s : %zu bytes, %zu tokens", p.c_str(), bytes, n);
    return (bytes > 0) ? JNI_TRUE : JNI_FALSE;
}

// ── Free ──────────────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_personai_app_core_LlamaEngine_nativeFree(
    JNIEnv*, jobject, jlong jhandle)
{
    auto* h = reinterpret_cast<LlamaHandle*>(jhandle);
    if (!h) return;
    if (h->ctx)   llama_free(h->ctx);
    if (h->model) llama_free_model(h->model);
    delete h;
    llama_backend_free();
    LOGI("Model freed");
}

} // extern "C"
