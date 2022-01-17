package at.tomtasche.reader.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.appintro.AppIntro;
import com.github.appintro.AppIntroFragment;
import com.github.appintro.model.SliderPage;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import at.tomtasche.reader.R;

public class IntroActivity extends AppIntro {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SliderPage sliderPage = createStyledPage();
        sliderPage.setTitle(getString(R.string.intro_title_open));
        sliderPage.setDescription(getString(R.string.intro_description_open));
        sliderPage.setImageDrawable(R.drawable.onboard1);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = createStyledPage();
        sliderPage.setTitle(getString(R.string.intro_title_edit));
        sliderPage.setDescription(getString(R.string.intro_description_edit));
        sliderPage.setImageDrawable(R.drawable.onboard2);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = createStyledPage();
        sliderPage.setTitle(getString(R.string.intro_title_apps));
        sliderPage.setDescription(getString(R.string.intro_description_apps));
        sliderPage.setImageDrawable(R.drawable.onboard3);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        setBarColor(Color.parseColor("#ffffff"));
        setSeparatorColor(Color.parseColor("#ffffff"));
        setColorDoneText(Color.parseColor("#6b6b6b"));
        setColorSkipButton(Color.parseColor("#b5b5b5"));
        setNextArrowColor(Color.parseColor("#6b6b6b"));
        setIndicatorColor(Color.parseColor("#b5b5b5"), Color.parseColor("#dadada"));

        setSkipButtonEnabled(true);
        setButtonsEnabled(true);
    }

    private SliderPage createStyledPage() {
        SliderPage sliderPage = new SliderPage();
        sliderPage.setTitleColor(Color.parseColor("#6b6b6b"));
        sliderPage.setDescriptionColor(Color.parseColor("#b5b5b5"));
        sliderPage.setBackgroundColor(Color.parseColor("#ffffff"));

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
}
