package at.tomtasche.reader.ui.widget;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.MainActivity;

// TODO: oh that's so dirty!
public class RecentlyDialog {

	private static final String FILENAME = "recent_documents";

	public static void showDialog(final MainActivity activity) {
		new Thread() {
			@Override
			public void run() {
				final Map<String, String> recentDocuments;
				try {
					recentDocuments = getRecentDocuments(activity
							.getApplicationContext());
					int size = recentDocuments.size();
					if (size == 0)
						return;

					final CharSequence[] items = new CharSequence[size];
					int i = 0;
					for (String title : recentDocuments.keySet()) {
						items[i] = title;

						i++;
					}

					activity.runOnUiThread(new Runnable() {

						@Override
						public void run() {
							AlertDialog.Builder builder = new AlertDialog.Builder(
									activity);
							builder.setTitle(R.string.dialog_recent_title);
							builder.setItems(items,
									new DialogInterface.OnClickListener() {

										public void onClick(
												DialogInterface dialog, int item) {
											activity.loadUri(Uri
													.parse(recentDocuments
															.get(items[item])));

											dialog.dismiss();
										}
									});
							builder.show();
						}
					});
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
	}

	private static Map<String, String> getRecentDocuments(Context context)
			throws IOException {
		Map<String, String> result = new HashMap<String, String>();

		FileInputStream input = null;
		InputStreamReader reader = null;
		BufferedReader bufferedReader = null;
		try {
			input = context.openFileInput(FILENAME);

			reader = new InputStreamReader(input);
			bufferedReader = new BufferedReader(reader);
			for (String s = bufferedReader.readLine(); s != null; s = bufferedReader
					.readLine()) {
				String[] temp = s.split(";;;");
				if (temp.length == 2)
					result.put(temp[0], temp[1]);
			}
		} finally {
			if (input != null)
				input.close();
			if (reader != null)
				reader.close();
			if (bufferedReader != null)
				bufferedReader.close();
		}

		return result;
	}

	public static void addRecentDocument(Context context, String title, Uri uri)
			throws IOException {
		if (title == null)
			return;

		FileOutputStream output = null;
		OutputStreamWriter writer = null;
		try {
			output = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
			writer = new OutputStreamWriter(output);
			writer.append(title + ";;;" + uri.toString());
			writer.flush();
		} finally {
			if (output != null)
				output.close();
			if (writer != null)
				writer.close();
		}
	}
}
