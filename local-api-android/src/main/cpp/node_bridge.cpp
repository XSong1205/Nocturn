#include <jni.h>
#include <android/log.h>
#include <node.h>

#include <atomic>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <string>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {
constexpr const char* kTag = "NocturnLocalApi";
std::atomic<bool> started{false};

std::string utf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool is_path(const std::string& path, bool directory) {
    struct stat metadata {};
    return !path.empty() && path.front() == '/' && stat(path.c_str(), &metadata) == 0 &&
           (directory ? S_ISDIR(metadata.st_mode) : S_ISREG(metadata.st_mode));
}

// Third-party API exceptions may contain cookies or request bodies. Forward
// only our deliberately non-sensitive readiness marker; never dump arbitrary
// JavaScript output into logcat on the user's device.
void consume_output(int descriptor, bool error_stream) {
    std::thread([descriptor, error_stream] {
        std::string line;
        char buffer[512];
        ssize_t count;
        bool reported_error = false;
        while ((count = read(descriptor, buffer, sizeof(buffer))) != 0) {
            if (count < 0) {
                if (errno == EINTR) continue;
                break;
            }
            for (ssize_t index = 0; index < count; ++index) {
                const char character = buffer[index];
                if (character == '\n') {
                    if (line.rfind("NOCTURN_LOCAL_API_READY ", 0) == 0) {
                        __android_log_write(ANDROID_LOG_INFO, kTag, "Local API server is listening on loopback.");
                    } else if (error_stream && !line.empty() && !reported_error) {
                        __android_log_write(ANDROID_LOG_WARN, kTag,
                            "Node runtime emitted diagnostics; raw output is suppressed to protect credentials.");
                        reported_error = true;
                    }
                    line.clear();
                } else if (line.size() < 1024) {
                    line.push_back(character);
                }
            }
        }
        close(descriptor);
    }).detach();
}

bool redirect_stream(int descriptor, bool error_stream) {
    int descriptors[2];
    if (pipe(descriptors) != 0) return false;
    if (dup2(descriptors[1], descriptor) == -1) {
        close(descriptors[0]);
        close(descriptors[1]);
        return false;
    }
    close(descriptors[1]);
    consume_output(descriptors[0], error_stream);
    return true;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_dev_nocturn_localapi_NodeNative_start(
    JNIEnv* env,
    jclass,
    jstring entry_path,
    jstring data_directory,
    jint port,
    jstring token
) {
    const std::string entry = utf8(env, entry_path);
    const std::string data = utf8(env, data_directory);
    const std::string secret = utf8(env, token);
    if (env->ExceptionCheck()) return -2;
    if (!is_path(entry, false) || !is_path(data, true) || port < 0 || port > 65535 || secret.size() < 32) {
        __android_log_write(ANDROID_LOG_ERROR, kTag, "Invalid Node runtime configuration.");
        return -2;
    }
    bool expected = false;
    if (!started.compare_exchange_strong(expected, true)) {
        __android_log_write(ANDROID_LOG_ERROR, kTag, "Node runtime cannot be started twice in one process.");
        return -1;
    }

    const std::string port_string = std::to_string(port);
    // This code executes inside the Android application process, never on the
    // developer's host. HOME is required by the API's configuration directory.
    if (setenv("NOCTURN_API_PORT", port_string.c_str(), 1) != 0 ||
        setenv("NOCTURN_API_TOKEN", secret.c_str(), 1) != 0 ||
        setenv("NOCTURN_DATA_DIR", data.c_str(), 1) != 0 ||
        setenv("HOME", data.c_str(), 1) != 0 ||
        setenv("TMPDIR", data.c_str(), 1) != 0) {
        __android_log_write(ANDROID_LOG_ERROR, kTag, "Unable to configure Node runtime environment.");
        return -3;
    }
    if (!redirect_stream(STDOUT_FILENO, false) || !redirect_stream(STDERR_FILENO, true)) {
        __android_log_write(ANDROID_LOG_ERROR, kTag, "Unable to redirect Node runtime diagnostics.");
        return -4;
    }

    // Node may rewrite argv; it must have mutable, contiguous storage with
    // lifetime spanning node::Start. No pointer refers into a JNI string.
    const std::string executable = "nocturn-node";
    std::vector<char> storage(executable.size() + entry.size() + 2);
    std::memcpy(storage.data(), executable.c_str(), executable.size() + 1);
    std::memcpy(storage.data() + executable.size() + 1, entry.c_str(), entry.size() + 1);
    char* arguments[] = {storage.data(), storage.data() + executable.size() + 1, nullptr};

    __android_log_write(ANDROID_LOG_INFO, kTag, "Starting embedded Node.js runtime.");
    const int result = node::Start(2, arguments);
    // node::Start must never be re-entered, even after a clean JS shutdown.
    __android_log_print(ANDROID_LOG_WARN, kTag, "Embedded Node.js exited with status %d.", result);
    return result;
}
