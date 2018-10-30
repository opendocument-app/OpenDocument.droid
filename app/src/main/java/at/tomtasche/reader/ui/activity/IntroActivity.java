package at.tomtasche.reader.ui.activity;

import android.graphics.Color;
import android.os.Bundle;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import at.tomtasche.reader.R;

public class IntroActivity extends AppIntro {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SliderPage sliderPage = new SliderPage();
        sliderPage.setTitle("hi");
        sliderPage.setDescription("hoooo");
        sliderPage.setImageDrawable(R.mipmap.icon);
        sliderPage.setBgColor(android.R.color.white);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        sliderPage = new SliderPage();
        sliderPage.setTitle("ho");
        sliderPage.setDescription("hiiiii");
        sliderPage.setImageDrawable(R.mipmap.icon);
        sliderPage.setBgColor(android.R.color.white);
        addSlide(AppIntroFragment.newInstance(sliderPage));

        setBarColor(android.R.color.white);
        setSeparatorColor(Color.parseColor("#6b6b6b"));
        setColorDoneText(Color.parseColor("#6b6b6b"));
        setColorSkipButton(Color.parseColor("#6b6b6b"));
        setNextArrowColor(Color.parseColor("#6b6b6b"));
        setIndicatorColor(Color.parseColor("#b5b5b5"), Color.parseColor("#dadada"));

        showSkipButton(true);
        setProgressButtonEnabled(true);
        setBackButtonVisibilityWithDone(true);
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
        super.onSlideChanged(oldFragment, newFragment);
    }
}
