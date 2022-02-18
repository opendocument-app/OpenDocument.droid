package at.tomtasche.reader.nonfree;

import android.net.Uri;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import androidx.annotation.NonNull;

import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.MainActivity;

public class ConfigManager {

    private boolean enabled;

    private boolean loaded;

    private FirebaseRemoteConfig remoteConfig;

    private final List<Runnable> callbacks;

    public ConfigManager() {
        callbacks = new LinkedList<>();
    }

    public void initialize() {
        if (!enabled) {
            return;
        }

        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.fetchAndActivate().addOnCompleteListener(new OnCompleteListener<Boolean>() {
            @Override
            public void onComplete(@NonNull Task<Boolean> task) {
                synchronized (callbacks) {
                    loaded = true;

                    for (Runnable callback : callbacks) {
                        callback.run();
                    }
                }
            }
        });
    }

    public boolean isLoaded() {
        if (!enabled) {
            return true;
        }

        return loaded;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void getBooleanConfig(String key, ConfigListener<Boolean> configListener) {
        if (!enabled) {
            configListener.onConfig(key, null);
            return;
        }

        synchronized (callbacks) {
            if (!loaded) {
                callbacks.add(new Runnable() {
                    @Override
                    public void run() {
                        getBooleanConfig(key, configListener);
                    }
                });

                return;
            }
        }

        boolean value = remoteConfig.getBoolean(key);
        configListener.onConfig(key, value);
    }

    public boolean getBooleanConfig(String key) {
        if (!enabled) {
            return false;
        }

        return remoteConfig.getBoolean(key);
    }

    public interface ConfigListener<T> {

        public void onConfig(String key, T value);
    }
}
