
package at.tomtasche.reader.error;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.os.Build;

public class ErrorReport {

    private static final String PACKAGE = "PACKAGE";

    private static final String ANDROID_VERSION = "ANDROID_VERSION";

    private static final String VERSION_CODE = "VERSION_CODE";

    private static final String VERSION_NAME = "VERSION_NAME";

    private static final String STACKTRACE = "STACKTRACE";

    private static final String MODEL = "MODEL";

    private static final String INFORMATION = "INFORMATION";

    public static void report(final Context context, final Exception e) throws Exception {
        final List<NameValuePair> formparams = new ArrayList<NameValuePair>();

        final ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(byteOutput);
        e.printStackTrace(stream);
        stream.flush();
        stream.close();
        byteOutput.flush();
        final String stackTrace = byteOutput.toString("UTF-8");
        byteOutput.close();

        formparams.add(new BasicNameValuePair(PACKAGE, context.getPackageName()));
        formparams.add(new BasicNameValuePair(STACKTRACE, stackTrace));
        formparams.add(new BasicNameValuePair(MODEL, Build.MODEL));
        formparams.add(new BasicNameValuePair(VERSION_CODE, Integer.toString(context
                .getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode)));
        formparams.add(new BasicNameValuePair(ANDROID_VERSION, Build.VERSION.SDK));
        formparams.add(new BasicNameValuePair(INFORMATION, "I"));
        formparams.add(new BasicNameValuePair(VERSION_NAME, context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName));

        final UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");

        final HttpPost request = new HttpPost("https://analydroid.appspot.com/analydroid/exception");
        request.setEntity(entity);

        final HttpClient client = new DefaultHttpClient();
        System.out.println(client.execute(request, new BasicResponseHandler()));
    }
}
