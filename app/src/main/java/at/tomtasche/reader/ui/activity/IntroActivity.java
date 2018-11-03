package at.tomtasche.reader.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import at.tomtasche.reader.R;

public class IntroActivity extends AppIntro {

    private boolean nextButtonReplaced = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SliderPage sliderPage = createStyledPage();
        sliderPage.setTitle("Open and read your ODF file on the go!");
        sliderPage.setDescription("OpenDocument Reader allows you to view documents that are stored in OpenDocument format (.odt, .ods, .odp). " +
                "These files are usually created using LibreOffice or OpenOffice. " +
                "This app allows to open those files on your mobile device too, so you can read them on the go.");
        sliderPage.setImageDrawable(R.drawable.onboard1);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = createStyledPage();
        sliderPage.setTitle("Found a typo in your document? Now supports modification!");
        sliderPage.setDescription("OpenDocument Reader not only allows to read documents on your mobile device, but also supports modifying them too. " +
                "Typos are fixed in a breeze, even on the train!");
        sliderPage.setImageDrawable(R.drawable.onboard2);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = createStyledPage();
        sliderPage.setTitle("Read your documents from within other apps");
        sliderPage.setDescription("OpenDocument Reader supports a huge range of other apps to open documents from. " +
                "A colleague sent a presentation via Gmail? " +
                "Click the attachment and this app is going to open right away!");
        sliderPage.setImageDrawable(R.drawable.onboard3);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        setBarColor(Color.parseColor("#ffffff"));
        setSeparatorColor(Color.parseColor("#ffffff"));
        setColorDoneText(Color.parseColor("#6b6b6b"));
        setColorSkipButton(Color.parseColor("#b5b5b5"));
        setNextArrowColor(Color.parseColor("#6b6b6b"));
        setIndicatorColor(Color.parseColor("#b5b5b5"), Color.parseColor("#dadada"));

        setDoneText("START");

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
