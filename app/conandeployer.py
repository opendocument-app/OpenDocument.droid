import glob
import os
import shutil

from conan.errors import ConanException

# conan architecture -> android abi directory name expected under jniLibs, and the
# ndk toolchain triple the matching libc++_shared.so lives under
ANDROID_ABIS = {
    "armv8": ("arm64-v8a", "aarch64-linux-android"),
    "armv7": ("armeabi-v7a", "arm-linux-androideabi"),
    "x86": ("x86", "i686-linux-android"),
    "x86_64": ("x86_64", "x86_64-linux-android"),
}


def deploy(graph, output_folder: str, **kwargs):
    conanfile = graph.root.conanfile
    conanfile.output.info(f"Custom deployer to {output_folder}")

    symlinks = conanfile.conf.get("tools.deployer:symlinks", check_type=bool, default=True)
    arch = conanfile.settings.get_safe("arch")

    conanfile.output.info(f"Symlinks: {symlinks}")
    conanfile.output.info(f"Arch: {arch}")

    deps = {dep.ref.name: dep for dep in conanfile.dependencies.values()}

    print(f"Dependencies: {list(deps.keys())}")

    copytree_kwargs = {"symlinks": symlinks, "dirs_exist_ok": True}

    # assets/ and jniLibs/ are separate android source sets, so the deployer writes the
    # whole build/conan/<arch> tree rather than a single directory
    assets_folder = f"{output_folder}/assets/core"

    if "odrcore" in deps:
        dep = deps["odrcore"]
        conanfile.output.info(f"Deploying odrcore assets to {assets_folder}")
        shutil.copytree(
            f"{dep.package_folder}/share",
            f"{assets_folder}/odrcore",
            # share/java holds odr-core-java.jar, which is deployed as a library
            # below - shipping it as an asset too would only bloat the apk
            ignore=shutil.ignore_patterns("java"),
            **copytree_kwargs,
        )

        # the java half of the JNI bindings. taking it from the very package that
        # built libodr_jni.so is what keeps the two halves in lockstep: they are one
        # ABI with no version negotiation, and a maven artifact resolved by version
        # could drift from the .so. it also keeps the build credential-free, which
        # f-droid and other clean source builders need
        libs_folder = f"{output_folder}/libs"
        os.makedirs(libs_folder, exist_ok=True)
        conanfile.output.info(f"Deploying odr-core-java.jar to {libs_folder}")
        shutil.copy2(
            f"{dep.package_folder}/share/java/odr-core-java.jar",
            f"{libs_folder}/odr-core-java.jar",
        )

    if "libmagic" in deps:
        dep = deps["libmagic"]
        conanfile.output.info(f"Deploying libmagic assets to {assets_folder}")
        shutil.copytree(
            f"{dep.package_folder}/res",
            f"{assets_folder}/libmagic",
            **copytree_kwargs,
        )

    if arch not in ANDROID_ABIS:
        raise ConanException(f"No android abi known for arch {arch}")
    abi, triple = ANDROID_ABIS[arch]
    jni_libs_folder = f"{output_folder}/jniLibs/{abi}"
    os.makedirs(jni_libs_folder, exist_ok=True)

    if "odrcore" in deps:
        dep = deps["odrcore"]
        # built by the recipe's with_jni option, alongside the odr-core-java.jar
        # deployed above - both halves come out of this one package
        source = f"{dep.package_folder}/lib/libodr_jni.so"
        conanfile.output.info(f"Deploying libodr_jni.so to {jni_libs_folder}")
        shutil.copy2(source, f"{jni_libs_folder}/libodr_jni.so")

    # libodr_jni.so links the shared c++ runtime (compiler.libcxx=c++_shared in
    # conanprofile.txt). the app no longer builds any native code of its own, so
    # nothing else would pull libc++_shared.so into the apk and the library would
    # fail to load at runtime with "library libc++_shared.so not found"
    ndk_path = conanfile.conf.get("tools.android:ndk_path")
    if not ndk_path:
        raise ConanException("tools.android:ndk_path is not set, cannot find libc++_shared.so")
    pattern = f"{ndk_path}/toolchains/llvm/prebuilt/*/sysroot/usr/lib/{triple}/libc++_shared.so"
    matches = glob.glob(pattern)
    if not matches:
        raise ConanException(f"No libc++_shared.so found at {pattern}")
    conanfile.output.info(f"Deploying libc++_shared.so to {jni_libs_folder}")
    shutil.copy2(matches[0], f"{jni_libs_folder}/libc++_shared.so")
