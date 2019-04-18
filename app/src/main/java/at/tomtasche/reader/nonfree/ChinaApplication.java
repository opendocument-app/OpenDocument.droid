package at.tomtasche.reader.nonfree;

import android.content.Context;

import com.apptutti.ad.ADApplication;

import androidx.multidex.MultiDex;

public class ChinaApplication extends ADApplication {

    @Override
    public void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
