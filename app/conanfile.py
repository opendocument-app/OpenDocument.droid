import os

from conan import ConanFile
from conan.tools.cmake import CMakeToolchain, CMakeDeps

required_conan_version = ">=2.0.6"


class OdrDroidConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"

    def requirements(self):
        self.requires("odrcore/4.1.0-pdf2htmlex-git", options={
            "with_pdf2htmlEX": True,
            "with_wvWare": True,
        })

    def generate(self):
        deps = CMakeDeps(self)
        deps.generate()

        tc = CMakeToolchain(self)
        tc.generate()

        asset_dir = os.path.join(self.build_folder, 'assets')
        os.mkdir(asset_dir)
        os.symlink(self.dependencies['pdf2htmlex'].cpp_info.resdirs[0], os.path.join(asset_dir, 'pdf2htmlEX'))
        os.symlink(self.dependencies['poppler-data'].cpp_info.resdirs[0], os.path.join(asset_dir, 'poppler-data'))
        os.symlink(self.dependencies['fontconfig'].cpp_info.resdirs[0], os.path.join(asset_dir, 'fontconfig'))
        os.symlink(self.dependencies['wvware'].cpp_info.resdirs[0], os.path.join(asset_dir, 'wv'))
