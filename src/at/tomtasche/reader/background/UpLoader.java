package at.tomtasche.reader.background;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.content.Context;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;
import at.tomtasche.reader.background.Document.Page;

import com.google.gson.Gson;

public class UpLoader extends AsyncTaskLoader<Document> implements FileLoader {

	private static final String SERVER_URL = "https://opendocument-engine.appspot.com/";

	private Uri uri;
	private Document document;

	public UpLoader(Context context, Uri uri) {
		super(context);

		this.uri = uri;
	}

	@Override
	public Throwable getLastError() {
		return null;
	}

	@Override
	public Uri getLastUri() {
		return uri;
	}

	@Override
	protected void onStartLoading() {
		super.onStartLoading();

		if (document != null) {
			deliverResult(document);
		} else {
			forceLoad();
		}
	}

	@Override
	protected void onReset() {
		super.onReset();

		onStopLoading();

		document = null;
	}

	@Override
	protected void onStopLoading() {
		super.onStopLoading();

		cancelLoad();
	}

	@Override
	public Document loadInBackground() {
		if (uri == DocumentLoader.URI_INTRO) {
			cancelLoad();

			return null;
		}

		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(SERVER_URL + "file");

		InputStream stream = null;
		ByteArrayOutputStream byteStream = null;
		try {
			stream = getContext().getContentResolver().openInputStream(uri);

			byteStream = new ByteArrayOutputStream();

			int bytesRead;
			byte[] buffer = new byte[1024];
			while ((bytesRead = stream.read(buffer)) != -1) {
				byteStream.write(buffer, 0, bytesRead);
			}

			byteStream.flush();

			byte[] data = byteStream.toByteArray();
			ByteArrayBody byteBody = new ByteArrayBody(data,
					uri.getLastPathSegment());

			MultipartEntity mpEntity = new MultipartEntity();
			mpEntity.addPart("file", byteBody);

			httppost.setEntity(mpEntity);
			HttpResponse response = httpclient.execute(httppost);
			if (response.getStatusLine().getStatusCode() == 200) {
				Map<String, Object> container = new Gson().fromJson(
						EntityUtils.toString(response.getEntity()), Map.class);

				String key = container.get("key").toString();
				URI viewerUri = URI
						.create("http://docs.google.com/viewer?embedded=true&url="
								+ URLEncoder.encode(SERVER_URL + "file?key="
										+ key, "UTF-8"));

				document = new Document();
				document.addPage(new Page("Document", viewerUri, 0));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				byteStream.close();
			} catch (IOException e) {
			}
			try {
				stream.close();
			} catch (IOException e) {
			}

			httpclient.getConnectionManager().shutdown();
		}

		return document;
	}
}
