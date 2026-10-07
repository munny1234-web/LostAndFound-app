package com.example.lostandfound;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class FaqActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_faq);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Toggle FAQ items
        setupFaq(R.id.layoutFaq1, R.id.tvAnswer1);
        setupFaq(R.id.layoutFaq2, R.id.tvAnswer2);
        setupFaq(R.id.layoutFaq3, R.id.tvAnswer3);
        setupFaq(R.id.layoutFaq4, R.id.tvAnswer4);
        setupFaq(R.id.layoutFaq5, R.id.tvAnswer5);
        setupFaq(R.id.layoutFaq6, R.id.tvAnswer6);
    }

    void setupFaq(int questionLayoutId, int answerId) {
        View layout = findViewById(questionLayoutId);
        TextView answer = findViewById(answerId);
        layout.setOnClickListener(v -> {
            if (answer.getVisibility() == View.VISIBLE) {
                answer.setVisibility(View.GONE);
            } else {
                answer.setVisibility(View.VISIBLE);
            }
        });
    }
}
