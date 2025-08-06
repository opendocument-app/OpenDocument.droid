#include "tmpfile_hack.hpp"

#include <string>
#include <optional>
#include <random>

#include <android/log.h>

#include <cstdlib>
#include <unistd.h>

static constexpr std::string_view s_filename_template = "tmpfile-XXXXXX";
static constexpr std::string_view s_default_directory = "/data/local/tmp";

static std::optional<std::string> s_tmpfile_directory;

std::string tmpfile_hack::get_tmpfile_directory() {
    if (s_tmpfile_directory.has_value()) {
        return s_tmpfile_directory.value();
    }

    if (const char *tmpfile_directory = std::getenv("TMPDIR"); tmpfile_directory != nullptr) {
        return tmpfile_directory;
    }

    return std::string(s_default_directory);
}

void tmpfile_hack::set_tmpfile_directory(std::string_view tmpfile_dir) {
    s_tmpfile_directory = std::string(tmpfile_dir);
}

extern "C" {

extern FILE *tmpfile() {
    std::string tmpfile_path =
            tmpfile_hack::get_tmpfile_directory() + "/" + std::string(s_filename_template);

    int descriptor = mkstemp(tmpfile_path.data());
    if (descriptor == -1) {
        __android_log_print(ANDROID_LOG_ERROR, "tmpfile_hack",
                            "Failed to create temporary file: %s", tmpfile_path.c_str());
        return nullptr;
    }
    __android_log_print(ANDROID_LOG_VERBOSE, "tmpfile_hack", "Temporary file created: %s",
                        tmpfile_path.c_str());

    FILE *handle = fdopen(descriptor, "w+b");
    unlink(tmpfile_path.c_str());

    if (handle == nullptr) {
        close(descriptor);
        __android_log_print(ANDROID_LOG_ERROR, "tmpfile_hack", "Failed to open temporary file: %s",
                            tmpfile_path.c_str());
    }
    return handle;
}

}
