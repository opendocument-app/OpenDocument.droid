#pragma once

#include <string>
#include <string_view>

namespace tmpfile_hack {

    std::string get_tmpfile_directory();

    void set_tmpfile_directory(std::string_view tmpfile_dir);

}
