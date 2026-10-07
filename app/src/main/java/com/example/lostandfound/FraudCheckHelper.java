package com.example.lostandfound;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Calls Hugging Face's hosted "Inference API" directly to get a REAL semantic
// (meaning-based) match score between a claimant's answer and the item's
// real hidden detail — using a pre-trained sentence-similarity ML model.
// No server of our own needed: Hugging Face hosts the model, we just call it.
public class FraudCheckHelper {

    // The token is NOT stored in code. Add this line to local.properties (project root):
    //   HF_TOKEN=your_token_here
    // Get a free "Read" token from https://huggingface.co/settings/tokens
    private static final String HF_TOKEN = BuildConfig.HF_TOKEN;

    private static final String API_URL =
            "https://router.huggingface.co/hf-inference/models/sentence-transformers/all-MiniLM-L6-v2/pipeline/sentence-similarity";

    public interface MatchCallback {
        void onResult(double matchPercentage, String verdict);
        void onError(String error);
    }

    public static void checkMatch(String claimAnswer, String realAnswer, MatchCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + HF_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                // The model can be "asleep" if unused for a while — first call
                // of the day may take 10-20s to warm up, so use a generous timeout.
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                JSONObject inputs = new JSONObject();
                inputs.put("source_sentence", claimAnswer);
                JSONArray sentences = new JSONArray();
                sentences.put(realAnswer);
                inputs.put("sentences", sentences);

                JSONObject body = new JSONObject();
                body.put("inputs", inputs);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                boolean ok = responseCode >= 200 && responseCode < 300;
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        ok ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                if (ok) {
                    // Response looks like: [0.873]  (one score per sentence we sent)
                    JSONArray scores = new JSONArray(sb.toString());
                    double similarity = scores.length() > 0 ? scores.getDouble(0) : 0.0;
                    double percentage = Math.max(0.0, Math.min(1.0, similarity)) * 100.0;
                    percentage = Math.round(percentage * 10.0) / 10.0;

                    String verdict;
                    if (percentage >= 80) verdict = "Strong Match";
                    else if (percentage >= 50) verdict = "Partial Match";
                    else verdict = "Weak Match";

                    double finalPercentage = percentage;
                    mainHandler.post(() -> callback.onResult(finalPercentage, verdict));
                } else {
                    mainHandler.post(() -> callback.onError("Server error: " + responseCode));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
