package com.example.lostandfound;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:cse2310042@adust.edu.bd"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Lost & Found App Feedback");
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        findViewById(R.id.btnFaq).setOnClickListener(v ->
            startActivity(new Intent(this, FaqActivity.class))
        );
    }
}
