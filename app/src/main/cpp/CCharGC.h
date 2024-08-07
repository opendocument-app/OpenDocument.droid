/*
 * CCharGC.h
 *
 * Copyright (c) 2019 ViliusSutkus89.com
 *
 * CCharGC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef PDF2HTMLEX_ANDROID_CCHARGC_H
#define PDF2HTMLEX_ANDROID_CCHARGC_H

#include <jni.h>

class CCharGC {
private:
    JNIEnv *env;
    jstring input;
    const char * cstr;

public:
    CCharGC(JNIEnv *env, jstring input) : env(env), input(input) {
        this->cstr = env->GetStringUTFChars(input, nullptr);
    }

    const char * c_str() const {
        return this->cstr;
    }

    bool isEmpty() const { return this->cstr[0] == '\0'; }

    ~CCharGC() {
        env->ReleaseStringUTFChars(this->input, this->cstr);
    }
};

#endif //PDF2HTMLEX_ANDROID_CCHARGC_H
