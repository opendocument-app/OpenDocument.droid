package at.tomtasche.reader.background;

import android.net.Uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Document {

    private final List<Page> pages;

    public Document() {
        this.pages = new ArrayList<>();
    }

    public Document(List<Page> pages) {
        this.pages = pages;
    }

    public List<Page> getPages() {
        return pages;
    }

    public void addPage(Page page) {
        pages.add(page);
    }

    public Page getPageAt(int index) {
        return pages.get(index);
    }

    public static class Page {

        private final String name;
        private final String url;
        private final int index;

        public Page(String name, URI url, int index) {
            this.name = name;
            this.url = url.toString();
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public Uri getUri() {
            return Uri.parse(url);
        }

        public int getIndex() {
            return index;
        }
    }
}
