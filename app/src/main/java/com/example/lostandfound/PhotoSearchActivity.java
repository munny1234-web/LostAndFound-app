package com.example.lostandfound;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhotoSearchActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 301;

    TextView btnBack, tvSearchStatus;
    LinearLayout layoutPickPhoto, layoutResults;
    ImageView imgQueryPreview;
    Button btnRunSearch;
    DatabaseReference dbRef;

    Uri queryImageUri;
    Bitmap queryBitmap;
    String queryLabelsText = "";
    String queryOcrText = "";
    String querySuggestedCategory = "";
    int mlCallbacksDone = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_search);

        dbRef = FirebaseDatabase.getInstance().getReference();
        btnBack = findViewById(R.id.btnBack);
        tvSearchStatus = findViewById(R.id.tvSearchStatus);
        layoutPickPhoto = findViewById(R.id.layoutPickPhoto);
        layoutResults = findViewById(R.id.layoutResults);
        imgQueryPreview = findViewById(R.id.imgQueryPreview);
        btnRunSearch = findViewById(R.id.btnRunSearch);

        btnBack.setOnClickListener(v -> finish());
        layoutPickPhoto.setOnClickListener(v -> pickImage());
        imgQueryPreview.setOnClickListener(v -> pickImage());
        btnRunSearch.setOnClickListener(v -> runPhotoSearch());
    }

    void pickImage() {
        Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null && data.getData() != null) {
            queryImageUri = data.getData();
            layoutPickPhoto.setVisibility(View.GONE);
            imgQueryPreview.setVisibility(View.VISIBLE);
            imgQueryPreview.setImageURI(queryImageUri);
            btnRunSearch.setEnabled(true);
            layoutResults.removeAllViews();
            tvSearchStatus.setText("");
        }
    }

    void runPhotoSearch() {
        if (queryImageUri == null) return;
        btnRunSearch.setEnabled(false);
        tvSearchStatus.setText("🤖 Analyzing your photo...");
        layoutResults.removeAllViews();
        mlCallbacksDone = 0;

        try {
            queryBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), queryImageUri);
        } catch (IOException e) {
            queryBitmap = null;
        }

        MLHelper.detectImageLabels(this, queryImageUri, new MLHelper.ImageLabelCallback() {
            @Override public void onResult(String suggestedCategory, List<String> allLabels) {
                querySuggestedCategory = suggestedCategory;
                queryLabelsText = String.join(", ", allLabels);
                mlCallbackReady();
            }
            @Override public void onError(String error) { mlCallbackReady(); }
        });

        MLHelper.detectTextInImage(this, queryImageUri, new MLHelper.TextDetectCallback() {
            @Override public void onResult(String detectedText) { queryOcrText = detectedText; mlCallbackReady(); }
            @Override public void onError(String error) { mlCallbackReady(); }
        });
    }

    void mlCallbackReady() {
        mlCallbacksDone++;
        if (mlCallbacksDone >= 2) {
            runOnUiThread(() -> {
                tvSearchStatus.setText("🔎 Comparing against posted items...");
                compareAgainstAllItems();
            });
        }
    }

    void compareAgainstAllItems() {
        dbRef.child("items").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<Item> scored = new ArrayList<>();
                List<Double> scores = new ArrayList<>();

                for (DataSnapshot s : snap.getChildren()) {
                    Item item = new Item();
                    item.id = s.getKey();
                    item.title = s.child("title").getValue(String.class);
                    item.description = s.child("description").getValue(String.class);
                    item.category = s.child("category").getValue(String.class);
                    item.location = s.child("location").getValue(String.class);
                    item.date = s.child("date").getValue(String.class);
                    item.contact = s.child("contact").getValue(String.class);
                    item.reward = s.child("reward").getValue(String.class);
                    item.imageUrl = s.child("imageUrl").getValue(String.class);
                    item.ocrText = s.child("ocrText").getValue(String.class);
                    item.labelsText = s.child("labelsText").getValue(String.class);
                    item.type = s.child("type").getValue(String.class);
                    item.userId = s.child("userId").getValue(String.class);
                    item.userName = s.child("userName").getValue(String.class);
                    Long ts = s.child("timestamp").getValue(Long.class);
                    item.timestamp = ts != null ? ts : 0;

                    double score = 0.0;

                    // Semantic label match (weight 40%) — strongest signal, same
                    // ML Kit labels used for regular post-to-post matching
                    if (!queryLabelsText.isEmpty() && item.labelsText != null && !item.labelsText.isEmpty()) {
                        score += MLHelper.calculateLabelSimilarity(queryLabelsText, item.labelsText) * 0.40;
                    }

                    // Visual/pixel similarity (weight 30%)
                    if (queryBitmap != null && item.imageUrl != null && !item.imageUrl.isEmpty() && !item.imageUrl.startsWith("http")) {
                        Bitmap itemBitmap = ImageUtils.decodeBase64(item.imageUrl);
                        if (itemBitmap != null) {
                            score += MLHelper.calculateImageSimilarity(queryBitmap, itemBitmap) * 0.30;
                        }
                    }

                    // OCR text overlap (weight 15%)
                    if (!queryOcrText.isEmpty() && item.ocrText != null && !item.ocrText.isEmpty()) {
                        score += MLHelper.calculateTextSimilarity(queryOcrText, item.ocrText) * 0.15;
                    }

                    // Category match bonus (weight 15%)
                    if (!querySuggestedCategory.isEmpty() && querySuggestedCategory.equalsIgnoreCase(item.category)) {
                        score += 0.15;
                    }

                    if (score > 0.12) {
                        item.matchScore = score;
                        scored.add(item);
                        scores.add(score);
                    }
                }

                // Sort descending by score
                for (int i = 0; i < scored.size() - 1; i++) {
                    for (int j = i + 1; j < scored.size(); j++) {
                        if (scored.get(j).matchScore > scored.get(i).matchScore) {
                            Item tmp = scored.get(i); scored.set(i, scored.get(j)); scored.set(j, tmp);
                        }
                    }
                }

                showResults(scored);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                tvSearchStatus.setText("Something went wrong. Please try again.");
                btnRunSearch.setEnabled(true);
            }
        });
    }

    void showResults(List<Item> results) {
        btnRunSearch.setEnabled(true);
        layoutResults.removeAllViews();

        if (results.isEmpty()) {
            tvSearchStatus.setText("😕 No similar items found. Try a clearer photo, or check back later.");
            return;
        }

        tvSearchStatus.setText("Found " + results.size() + " possible match(es):");
        int count = Math.min(results.size(), 10);
        for (int i = 0; i < count; i++) {
            Item match = results.get(i);
            View card = getLayoutInflater().inflate(R.layout.item_match_card, layoutResults, false);
            ((TextView) card.findViewById(R.id.tvMatchScore)).setText(MLHelper.getSimilarityText(match.matchScore));
            ((TextView) card.findViewById(R.id.tvMatchTitle)).setText(match.title != null ? match.title : "(untitled)");
            ((TextView) card.findViewById(R.id.tvMatchMeta)).setText(
                    "📂 " + (match.category != null ? match.category : "") +
                    "   📍 " + (match.location != null ? match.location : ""));
            ImageView imgThumb = card.findViewById(R.id.imgMatchThumb);
            ImageUtils.loadItemImage(this, match.imageUrl, imgThumb, R.drawable.chip_default);
            card.setOnClickListener(v -> {
                Intent i2 = new Intent(PhotoSearchActivity.this, ItemDetailsActivity.class);
                i2.putExtra("ITEM", match);
                startActivity(i2);
            });
            layoutResults.addView(card);
        }
    }
}
