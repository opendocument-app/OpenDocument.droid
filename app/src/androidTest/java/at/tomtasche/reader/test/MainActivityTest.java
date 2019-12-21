package at.tomtasche.reader.test;


import android.os.Environment;
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
public class MainActivityTest {

    private boolean loadingDone;

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
            final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "test.odt");
            file.mkdirs();
            file.createNewFile();

            InputStream inputStream = new URL("https://api.libreoffice.org/examples/cpp/DocumentLoader/test.odt").openStream();
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
                allOf(withId(android.R.id.text1), withText("test.odt"),
                        isDisplayed()));
        textView2.perform(click());

        ViewInteraction appCompatButton = onView(
                allOf(withId(R.id.nnf_button_ok), withText("OK"),
                        isDisplayed()));
        appCompatButton.perform(click());

        //pressBack();

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

        ViewInteraction actionMenuItemView2 = onView(
                allOf(withId(R.id.menu_edit), withContentDescription("Edit document"),
                        isEnabled()));
        actionMenuItemView2.withFailureHandler(new FailureHandler() {
            @Override
            public void handle(Throwable error, Matcher<View> viewMatcher) {
                // fails on small screens, try again with overflow menu

                ViewInteraction overflowMenuButton = onView(
                        allOf(withContentDescription("More options"),
                                isDisplayed()));
                overflowMenuButton.perform(click());

                ViewInteraction actionMenuItemView2 = onView(
                        allOf(withId(R.id.menu_edit), withContentDescription("Edit document"),
                                isDisplayed()));
                actionMenuItemView2.perform(click());
            }
        });
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
