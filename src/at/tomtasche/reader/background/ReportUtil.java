package at.tomtasche.reader.background;

import java.io.PrintWriter;
import java.io.StringWriter;

import android.annotation.TargetApi;
import android.app.ApplicationErrorReport;
import android.content.Context;
import android.content.Intent;

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
}
