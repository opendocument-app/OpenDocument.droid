package at.tomtasche.reader.background;

import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

public class Document implements Parcelable {

    public static final Parcelable.Creator<Document> CREATOR = new Parcelable.Creator<Document>() {
	public Document createFromParcel(Parcel in) {
	    return new Document(in);
	}

	public Document[] newArray(int size) {
	    return new Document[size];
	}
    };

    private final List<Page> pages;

    public Document(List<Page> pages) {
	this.pages = pages;
    }

    private Document(Parcel in) {
	pages = new ArrayList<Page>();

	in.readList(pages, Page.class.getClassLoader());
    }

    public List<Page> getPages() {
	return pages;
    }

    public Page getPageAt(int index) {
	return pages.get(index);
    }

    @Override
    public int describeContents() {
	return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
	dest.writeList(pages);
    }

    public static class Page implements Parcelable {

	public static final Parcelable.Creator<Page> CREATOR = new Parcelable.Creator<Page>() {
	    public Page createFromParcel(Parcel in) {
		return new Page(in);
	    }

	    public Page[] newArray(int size) {
		return new Page[size];
	    }
	};

	private final String name;
	private final String html;
	private final int index;

	public Page(String name, String html, int index) {
	    this.name = name;
	    this.html = html;
	    this.index = index;
	}

	private Page(Parcel in) {
	    name = in.readString();
	    html = in.readString();
	    index = in.readInt();
	}

	public String getName() {
	    return name;
	}

	public String getHtml() {
	    return html;
	}

	public int getIndex() {
	    return index;
	}

	@Override
	public int describeContents() {
	    return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
	    dest.writeString(name);
	    dest.writeString(html);
	    dest.writeInt(index);
	}
    }
}
