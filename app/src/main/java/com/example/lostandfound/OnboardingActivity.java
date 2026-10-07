package com.example.lostandfound;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

    LinearLayout page1, page2, page3;
    Button btnNext, btnSkip, btnGetStarted;
    TextView tvDot1, tvDot2, tvDot3;
    int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        page1 = findViewById(R.id.page1);
        page2 = findViewById(R.id.page2);
        page3 = findViewById(R.id.page3);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);
        btnGetStarted = findViewById(R.id.btnGetStarted);
        tvDot1 = findViewById(R.id.tvDot1);
        tvDot2 = findViewById(R.id.tvDot2);
        tvDot3 = findViewById(R.id.tvDot3);

        showPage(0);

        btnNext.setOnClickListener(v -> {
            currentPage++;
            if (currentPage > 2) currentPage = 2;
            showPage(currentPage);
        });

        btnSkip.setOnClickListener(v -> finish_onboarding());
        btnGetStarted.setOnClickListener(v -> finish_onboarding());
    }

    void showPage(int page) {
        page1.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        page2.setVisibility(page == 1 ? View.VISIBLE : View.GONE);
        page3.setVisibility(page == 2 ? View.VISIBLE : View.GONE);

        tvDot1.setTextColor(page == 0 ? 0xFF6C63FF : 0xFFCCCCCC);
        tvDot2.setTextColor(page == 1 ? 0xFF6C63FF : 0xFFCCCCCC);
        tvDot3.setTextColor(page == 2 ? 0xFF6C63FF : 0xFFCCCCCC);

        btnNext.setVisibility(page < 2 ? View.VISIBLE : View.GONE);
        btnSkip.setVisibility(page < 2 ? View.VISIBLE : View.GONE);
        btnGetStarted.setVisibility(page == 2 ? View.VISIBLE : View.GONE);
    }

    void finish_onboarding() {
        SharedPreferences prefs = getSharedPreferences("LostFoundPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_done", true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
