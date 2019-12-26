package at.tomtasche.reader.test;


import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.FailureHandler;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;
import at.tomtasche.reader.R;
import at.tomtasche.reader.ui.activity.MainActivity;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class PdfActivityTest {

    private boolean loadingDone;
    private boolean loadingSuccess;

    @Rule
    public ActivityTestRule<MainActivity> mActivityTestRule = new ActivityTestRule<>(MainActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule =
            GrantPermissionRule.grant(
                    "android.permission.WRITE_EXTERNAL_STORAGE");

    private static void copy(InputStream src, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = src.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            src.close();
        }
    }

    @Test
    public void mainActivityTest() {
        try {
            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AAA/test.pdf");
            file.getParentFile().mkdirs();
            file.createNewFile();

            InputStream inputStream = new URL("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf").openStream();
            copy(inputStream, file);
        } catch (IOException e) {
            e.printStackTrace();

            assert false;
        }

        ViewInteraction actionMenuItemView = onView(
                allOf(withId(R.id.menu_open), withContentDescription("Open document"),
                        isDisplayed()));
        actionMenuItemView.perform(click());

        ViewInteraction appCompatTextView = onView(
                allOf(withId(android.R.id.text1), withText("Choose file from device"),
                        isDisplayed()));
        appCompatTextView.perform(click());

        ViewInteraction textView = onView(
                allOf(withId(android.R.id.text1), withText("Download"),
                        isDisplayed()));
        textView.perform(click());

        ViewInteraction textView2 = onView(
                allOf(withId(android.R.id.text1), withText("AAA"),
                        isDisplayed()));
        textView2.perform(click());

        ViewInteraction textView3 = onView(
                allOf(withId(android.R.id.text1), withText("test.pdf"),
                        isDisplayed()));
        textView3.perform(click());

        ViewInteraction appCompatButton = onView(
                allOf(withId(R.id.nnf_button_ok), withText("OK"),
                        isDisplayed()));
        appCompatButton.perform(click());

        //pressBack();

        Log.e("smn", "1");

        do {
            ViewInteraction loadingDialog = onView(
                    allOf(withId(android.R.id.message), withText("This could take a few minutes, depending on the structure of your document and the processing power of your device."),
                            isDisplayed()));
            loadingDialog.withFailureHandler(new FailureHandler() {
                @Override
                public void handle(Throwable error, Matcher<View> viewMatcher) {
                    // awful code right here. official method using "IdlingResource" seems weird too though

                    loadingDone = true;
                }
            });
            loadingDialog.check(matches(withText("This could take a few minutes, depending on the structure of your document and the processing power of your device.")));
        } while (!loadingDone);

        Log.e("smn", "2");

        ViewInteraction errorDialog = onView(
                allOf(withText("This doesn't seem to be a supported file format."),
                        isDisplayed()));
        errorDialog.withFailureHandler(new FailureHandler() {
            @Override
            public void handle(Throwable error, Matcher<View> viewMatcher) {
                // awful code right here. official method using "IdlingResource" seems weird too though

                loadingSuccess = true;
            }
        });
        errorDialog.check(matches(withText("This doesn't seem to be a supported file format.")));

        Log.e("smn", "3");

        if (!loadingSuccess) {
            throw new RuntimeException("PDF could not be loaded");
        }
    }
}
