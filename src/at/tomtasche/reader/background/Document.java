package at.tomtasche.reader.background;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import android.net.Uri;
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
	private boolean limited;

	public Document() {
		pages = new ArrayList<Page>();
	}

	public Document(List<Page> pages) {
		this.pages = pages;
	}

	private Document(Parcel in) {
		pages = new ArrayList<Page>();

		in.readList(pages, Page.class.getClassLoader());
		limited = in.readInt() == 1;
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

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeList(pages);
		dest.writeInt(limited ? 1 : 0);
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
		private final String url;
		private final int index;

		public Page(String name, URI url, int index) {
			this.name = name;
			this.url = url.toString();
			this.index = index;
		}

		private Page(Parcel in) {
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

		public Uri getUri() {
			return Uri.parse(url);
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
