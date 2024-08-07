package app.opendocument.android.pdf2htmlex;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.viliussutkus89.android.assetextractor.AssetExtractor;
import com.viliussutkus89.android.fontconfigtranslator.FontconfigTranslator;

import java.io.File;


final class FontconfigAndroid {
    static void init(@NonNull AssetManager assetManager, @NonNull File cacheDir, @NonNull File filesDir) {
        File xdgCache = new File(cacheDir, "xdg-cache");
        xdgCache.mkdir();
        EnvVar.set("XDG_CACHE_HOME", xdgCache.getAbsolutePath());

        AssetExtractor ae = new AssetExtractor(assetManager).setNoOverwrite();
        File fontsConfigDir = ae.extract(new File(filesDir, "etc"), "fontconfig/fontconfig/conf.avail");
        ae.extract(fontsConfigDir, "pdf2htmlEX/etc/fonts/local.conf");
        EnvVar.set("FONTCONFIG_PATH", fontsConfigDir.getAbsolutePath());

        ae.extract(new File(filesDir, "share"), "pdf2htmlEX/share/fonts");

        try {
            File fontconfigGeneratedXml = new File(fontsConfigDir, "system-etc-fonts-xml-translated.conf");
            FontconfigTranslator.translate(fontconfigGeneratedXml);
        } catch (Exception e) {
            Log.e("Fontconfig", "Fontconfig translator failed: ", e);
        }
    }
}
