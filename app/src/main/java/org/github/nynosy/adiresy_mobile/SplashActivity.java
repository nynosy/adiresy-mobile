package org.github.nynosy.adiresy_mobile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

import org.github.nynosy.adiresy_mobile.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private static final String[] SLOGANS = {
            "Your address in Madagascar, in one code",
            "Votre adresse à Madagascar, en un code",
            "Ny adiresinao any Madagasikara, amin'ny kaody iray",
    };

    private static final long DURATION_MS = 1800;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.labelSlogan.setText(SLOGANS[new Random().nextInt(SLOGANS.length)]);

        handler.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, DURATION_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
