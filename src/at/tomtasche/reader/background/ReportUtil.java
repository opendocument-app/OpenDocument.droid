package at.tomtasche.reader.background;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ApplicationErrorReport;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import at.tomtasche.reader.R;

public class ReportUtil {

	@TargetApi(14)
	public static Intent createFeedbackIntent(Context context, Throwable error) {
		ApplicationErrorReport report = new ApplicationErrorReport();
		report.packageName = report.processName = context.getPackageName();
		report.time = System.currentTimeMillis();
		report.type = ApplicationErrorReport.TYPE_CRASH;
		report.systemApp = false;

		ApplicationErrorReport.CrashInfo crash = new ApplicationErrorReport.CrashInfo();
		crash.exceptionClassName = error.getClass().getSimpleName();
		crash.exceptionMessage = error.getMessage();

		StringWriter writer = new StringWriter();
		PrintWriter printer = new PrintWriter(writer);
		error.printStackTrace(printer);

		crash.stackTrace = writer.toString();

		StackTraceElement stack = error.getStackTrace()[0];
		crash.throwClassName = stack.getClassName();
		crash.throwFileName = stack.getFileName();
		crash.throwLineNumber = stack.getLineNumber();
		crash.throwMethodName = stack.getMethodName();

		report.crashInfo = crash;

		Intent intent = new Intent(Intent.ACTION_APP_ERROR);
		intent.putExtra(Intent.EXTRA_BUG_REPORT, report);

		return intent;
	}

	public static void submitFile(final Context context, final Throwable error,
			final Uri uri, final int errorDescription) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.toast_error_generic);
		builder.setMessage(context.getString(errorDescription)
				+ System.getProperty("line.separator")
				+ context.getString(R.string.dialog_submit_file));
		builder.setNegativeButton(android.R.string.no, null);
		builder.setNeutralButton(R.string.dialog_error_send_error_only,
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						context.startActivity(ReportUtil.createFeedbackIntent(
								context, error));
					}
				});
		builder.setPositiveButton(android.R.string.yes, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				Bundle bundle = new Bundle();
				bundle.putStringArray(Intent.EXTRA_EMAIL,
						new String[] { "tomtasche+reader@gmail.com" });
				bundle.putParcelable(Intent.EXTRA_STREAM, uri);

				String version;
				try {
					version = context.getPackageManager().getPackageInfo(
							context.getPackageName(), 0).versionName;
				} catch (NameNotFoundException e1) {
					version = "unknown";
				}
				bundle.putString(Intent.EXTRA_SUBJECT, "OpenDocument Reader ("
						+ version + "): Couldn't open file");

				StringWriter writer = new StringWriter();
				PrintWriter printer = new PrintWriter(writer);
				printer.println("Important information for the developer:");
				printer.println("- " + Build.MODEL + " running Android "
						+ Build.VERSION.SDK_INT);
				printer.println("- The following error occured while opening the file located at: "
						+ uri.toString());
				printer.println(context.getString(errorDescription));
				printer.println();
				error.printStackTrace(printer);
				printer.println();
				printer.println("-----------------");
				printer.println("Feel free to append further information here.");

				try {
					printer.close();
					writer.close();
				} catch (IOException e) {
				}

				bundle.putString(Intent.EXTRA_TEXT, writer.toString());

				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("plain/text");
				intent.putExtras(bundle);

				context.startActivity(Intent.createChooser(intent,
						context.getString(R.string.dialog_submit_file_title)));

				dialog.dismiss();
			}
		});

		builder.show();
	}
}
