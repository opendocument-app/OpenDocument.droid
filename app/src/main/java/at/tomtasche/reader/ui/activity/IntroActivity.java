package at.tomtasche.reader.ui.activity;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import at.tomtasche.reader.R;

public class IntroActivity extends AppIntro {

    private boolean nextButtonReplaced = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SliderPage sliderPage = createStyledPage();
        sliderPage.setTitle("Open and read your ODF file on the go!");
        sliderPage.setDescription("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Sit amet consectetur adipiscing elit ut aliquam. Porttitor massa id neque aliquam vestibulum morbi blandit cursus risus.\n" +
                "\n");
        sliderPage.setImageDrawable(R.drawable.onboard1);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = createStyledPage();
        sliderPage.setTitle("Found any typo in your document? Now supports modification!");
        sliderPage.setDescription("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Sit amet consectetur adipiscing elit ut aliquam. Porttitor massa id neque aliquam vestibulum morbi blandit cursus risus.\n" +
                "\n");
        sliderPage.setImageDrawable(R.drawable.onboard2);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = createStyledPage();
        sliderPage.setTitle("Read your documents within other apps");
        sliderPage.setDescription("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Sit amet consectetur adipiscing elit ut aliquam. Porttitor massa id neque aliquam vestibulum morbi blandit cursus risus.\n" +
                "\n");
        sliderPage.setImageDrawable(R.drawable.onboard3);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        setBarColor(Color.parseColor("#ffffff"));
        setSeparatorColor(Color.parseColor("#ffffff"));
        setColorDoneText(Color.parseColor("#6b6b6b"));
        setColorSkipButton(Color.parseColor("#b5b5b5"));
        setNextArrowColor(Color.parseColor("#6b6b6b"));
        setIndicatorColor(Color.parseColor("#b5b5b5"), Color.parseColor("#dadada"));

        showSkipButton(true);
        setProgressButtonEnabled(true);
        setBackButtonVisibilityWithDone(true);
    }

    private SliderPage createStyledPage() {
        SliderPage sliderPage = new SliderPage();
        sliderPage.setTitleColor(Color.parseColor("#6b6b6b"));
        sliderPage.setDescColor(Color.parseColor("#b5b5b5"));
        sliderPage.setBgColor(Color.parseColor("#ffffff"));

        return sliderPage;
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);

        finish();
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);

        finish();
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        if (newFragment == null) {
            super.onSlideChanged(oldFragment, newFragment);

            return;
        }

        boolean isLastSlide = pager.getCurrentItem() == (slidesNumber - 1);
        // replace image for next-button with text
        if (!nextButtonReplaced && !isLastSlide) {
            View oldNextButton = findViewById(R.id.next);
            ViewGroup buttonParent = (ViewGroup) oldNextButton.getParent();
            int index = buttonParent.indexOfChild(oldNextButton);
            buttonParent.removeView(oldNextButton);

            TextView newNextButton = (TextView) getLayoutInflater().inflate(R.layout.appintro_button_copy, buttonParent, false);
            newNextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pager.goToNextSlide();
                }
            });
            newNextButton.setTextColor(Color.parseColor("#6b6b6b"));
            buttonParent.addView(newNextButton, index);

            nextButtonReplaced = true;
        } else {
            View newNextButton = findViewById(R.id.next);
            setButtonState(newNextButton, !isLastSlide);
        }

        // change order of image and text
        View imageView = newFragment.getView().findViewById(R.id.image);
        ViewGroup imageParent = (ViewGroup) imageView.getParent();
        ViewGroup contentParent = (ViewGroup) imageParent.getParent();
        contentParent.removeView(imageParent);
        contentParent.addView(imageParent, 0);

        // change line spacing for big screens
        if (getResources().getConfiguration().screenHeightDp > 600) {
            TextView descriptionView = newFragment.getView().findViewById(R.id.description);
            descriptionView.setLineSpacing(0, 1.5f);
        }

        super.onSlideChanged(oldFragment, newFragment);
    }
}
