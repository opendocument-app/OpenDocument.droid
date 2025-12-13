package at.tomtasche.reader.nonfree;

import java.util.LinkedList;
import java.util.List;

public class ConfigManager {

    private boolean enabled;

    private boolean loaded;

    private final List<Runnable> callbacks;

    public ConfigManager() {
        callbacks = new LinkedList<>();
    }

    public void initialize() {
        if (!enabled) {
            return;
        }

        synchronized (callbacks) {
            loaded = true;

            for (Runnable callback : callbacks) {
                callback.run();
            }
        }
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

        boolean value = getBooleanConfig(key);
        configListener.onConfig(key, value);
    }

    public boolean getBooleanConfig(String key) {
        if (!enabled) {
            return false;
        }

        return false;
    }

    public interface ConfigListener<T> {

        void onConfig(String key, T value);
    }
}
