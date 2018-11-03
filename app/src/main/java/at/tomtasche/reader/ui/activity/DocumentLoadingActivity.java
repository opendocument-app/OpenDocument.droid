package at.tomtasche.reader.ui.activity;

import android.net.Uri;

import at.tomtasche.reader.background.DocumentLoader;

public interface DocumentLoadingActivity {

    public void loadUri(Uri uri);
}
