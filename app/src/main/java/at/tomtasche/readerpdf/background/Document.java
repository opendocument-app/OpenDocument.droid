package at.tomtasche.readerpdf.background;

import android.net.Uri;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import at.stefl.opendocument.java.odf.OpenDocument;

public class Document {

    private final OpenDocument origin;
    private final List<Page> pages;
    private boolean limited;

    public Document(OpenDocument origin) {
        this.origin = origin;

        pages = new ArrayList<Page>();
    }

    public Document(OpenDocument origin, List<Page> pages) {
        this.origin = origin;
        this.pages = pages;
    }

    public OpenDocument getOrigin() {
        return origin;
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

    public boolean isLimited() {
        return limited;
    }

    public void setLimited(boolean limited) {
        this.limited = limited;
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
