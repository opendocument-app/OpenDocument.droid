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
        # @TODO: figure out how to use POPPLER_DATA_DIR exported by poppler-data
        tc.variables["POPPLER_DATA_RES_DIR"] = self.dependencies['poppler-data'].cpp_info.resdirs[0]
        tc.variables["PDF2HTMLEX_RES_DIR"] = self.dependencies['pdf2htmlex'].cpp_info.resdirs[0]
        tc.variables["FONTCONFIG_RES_DIR"] = self.dependencies['fontconfig'].cpp_info.resdirs[0]
        tc.variables["WVWARE_RES_DIR"] = self.dependencies['wvware'].cpp_info.resdirs[0]
        tc.generate()
