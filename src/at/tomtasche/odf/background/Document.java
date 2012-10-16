package at.tomtasche.odf.background;

import java.net.URI;
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

    private final List<Part> pages;

    public Document() {
	pages = new ArrayList<Part>();
    }

    public Document(List<Part> pages) {
	this.pages = pages;
    }

    private Document(Parcel in) {
	pages = new ArrayList<Part>();

	in.readList(pages, Part.class.getClassLoader());
    }

    public List<Part> getPages() {
	return pages;
    }

    public void addPage(Part page) {
	pages.add(page);
    }

    public Part getPageAt(int index) {
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

    public static class Part implements Parcelable {

	public static final Parcelable.Creator<Part> CREATOR = new Parcelable.Creator<Part>() {
	    public Part createFromParcel(Parcel in) {
		return new Part(in);
	    }

	    public Part[] newArray(int size) {
		return new Part[size];
	    }
	};

	private final String name;
	private final String url;
	private final int index;

	public Part(String name, URI url, int index) {
	    this.name = name;
	    this.url = url.toString();
	    this.index = index;
	}

	private Part(Parcel in) {
	    name = in.readString();
	    url = in.readString();
	    index = in.readInt();
	}

	public String getName() {
	    return name;
	}

	public String getUrl() {
	    return url;
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
	    dest.writeString(url);
	    dest.writeInt(index);
	}
    }
}
