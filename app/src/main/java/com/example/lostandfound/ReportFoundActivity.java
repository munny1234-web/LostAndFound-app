package com.example.lostandfound;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportFoundActivity extends AppCompatActivity {
    EditText etItemName, etDescription, etLocation, etContact, etVerificationQuestion, etVerificationAnswer;
    Spinner spinnerCategory;
    Button btnSubmit, btnPickDate, btnPickLocation;
    Double pickedLat, pickedLng;
    TextView tvFoundDate, btnBack;
    LinearLayout layoutPhoto;
    ImageView imgPreview;
    TextView tvMLSuggestion;
    LinearLayout layoutMLSuggestion;

    // ---- multi-photo state ----
    LinearLayout layoutExtraThumbs;
    TextView tvAddMore;
    Button btnRemoveCover;
    static final int MAX_PHOTOS = 4;
    final ArrayList<Uri> photoUris = new ArrayList<>(); // index 0 = cover

    Calendar selectedDate = Calendar.getInstance();
    String ocrText = "";
    String labelsText = "";
    private static final int PICK_IMAGE = 101;
    private static final int PICK_LOCATION = 102;
    String[] categories = {"Electronics","Wallet/Purse","Keys","Phone","Documents","Jewelry","Bag","Clothing","Glasses","Bottle/Container","Umbrella","Toy","Sports Equipment","Musical Instrument","Pet","Other"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_found);

        etItemName = findViewById(R.id.etItemName);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        etContact = findViewById(R.id.etContact);
        etVerificationQuestion = findViewById(R.id.etVerificationQuestion);
        etVerificationAnswer = findViewById(R.id.etVerificationAnswer);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnPickDate = findViewById(R.id.btnPickDate);
        btnPickLocation = findViewById(R.id.btnPickLocation);
        tvFoundDate = findViewById(R.id.tvFoundDate);
        btnBack = findViewById(R.id.btnBack);
        layoutPhoto = findViewById(R.id.layoutPhoto);
        imgPreview = findViewById(R.id.imgPreview);
        tvMLSuggestion = findViewById(R.id.tvMLSuggestion);
        layoutMLSuggestion = findViewById(R.id.layoutMLSuggestion);
        layoutExtraThumbs = findViewById(R.id.layoutExtraThumbs);
        tvAddMore = findViewById(R.id.tvAddMore);
        btnRemoveCover = findViewById(R.id.btnRemoveCover);

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(a);
        updateDate();

        btnBack.setOnClickListener(v -> finish());

        layoutPhoto.setOnClickListener(v -> launchPicker());
        imgPreview.setOnClickListener(v -> launchPicker()); // tap the cover to add another photo
        tvAddMore.setOnClickListener(v -> launchPicker());
        btnRemoveCover.setOnClickListener(v -> {
            if (!photoUris.isEmpty()) { photoUris.remove(0); refreshPhotoUI(); }
        });

        btnPickDate.setOnClickListener(v ->
                new DatePickerDialog(this, (view, y, m, d) -> {
                    selectedDate.set(y, m, d);
                    updateDate();
                }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                        selectedDate.get(Calendar.DAY_OF_MONTH)).show()
        );

        btnPickLocation.setOnClickListener(v ->
                startActivityForResult(new Intent(this, LocationPickerActivity.class), PICK_LOCATION));

        btnSubmit.setOnClickListener(v -> submit());
        refreshPhotoUI();
    }

    void launchPicker() {
        if (photoUris.size() >= MAX_PHOTOS) {
            Toast.makeText(this, "You can add up to " + MAX_PHOTOS + " photos.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    void updateDate() {
        tvFoundDate.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.getTime()));
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null && data.getData() != null) {
            boolean wasEmpty = photoUris.isEmpty();
            photoUris.add(data.getData());
            refreshPhotoUI();

            if (wasEmpty) {
                // 🤖 ML: Auto detect category from the cover photo only
                layoutMLSuggestion.setVisibility(View.VISIBLE);
                tvMLSuggestion.setText("🤖 Analyzing image...");
                Uri cover = photoUris.get(0);

                MLHelper.detectImageLabels(this, cover, new MLHelper.ImageLabelCallback() {
                    @Override
                    public void onResult(String suggestedCategory, java.util.List<String> allLabels) {
                        runOnUiThread(() -> {
                            tvMLSuggestion.setText("🤖 AI suggests: " + suggestedCategory);
                            for (int i = 0; i < categories.length; i++) {
                                if (categories[i].equals(suggestedCategory)) {
                                    spinnerCategory.setSelection(i);
                                    break;
                                }
                            }
                        });
                        labelsText = String.join(", ", allLabels);
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> tvMLSuggestion.setText("🤖 Could not detect category"));
                    }
                });

                MLHelper.detectTextInImage(this, cover, new MLHelper.TextDetectCallback() {
                    @Override public void onResult(String detectedText) { ocrText = detectedText; }
                    @Override public void onError(String error) { /* OCR is best-effort, ignore failures */ }
                });
            }
        } else if (req == PICK_LOCATION && res == RESULT_OK && data != null) {
            pickedLat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0);
            pickedLng = data.getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0);
            String address = data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS);
            if (address != null && !address.isEmpty()) etLocation.setText(address);
        }
    }

    // Rebuilds the photo section: empty-state box, cover preview, extra-photo
    // thumbnail strip and the "add another" affordance, based on photoUris.
    void refreshPhotoUI() {
        boolean hasPhotos = !photoUris.isEmpty();
        layoutPhoto.setVisibility(hasPhotos ? View.GONE : View.VISIBLE);
        imgPreview.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        btnRemoveCover.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);

        if (hasPhotos) imgPreview.setImageURI(photoUris.get(0));

        layoutExtraThumbs.removeAllViews();
        if (photoUris.size() > 1) {
            layoutExtraThumbs.setVisibility(View.VISIBLE);
            float d = getResources().getDisplayMetrics().density;
            for (int i = 1; i < photoUris.size(); i++) {
                final int idx = i;
                FrameLayout thumbWrap = new FrameLayout(this);
                thumbWrap.setLayoutParams(new LinearLayout.LayoutParams((int) (70 * d), (int) (70 * d)));
                ((LinearLayout.LayoutParams) thumbWrap.getLayoutParams()).setMarginEnd((int) (8 * d));

                ImageView thumb = new ImageView(this);
                thumb.setLayoutParams(new FrameLayout.LayoutParams((int) (70 * d), (int) (70 * d)));
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setImageURI(photoUris.get(idx));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(10 * d);
                thumb.setClipToOutline(true);
                thumb.setBackground(bg);

                Button remove = new Button(this);
                FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams((int) (22 * d), (int) (22 * d));
                rlp.gravity = Gravity.TOP | Gravity.END;
                rlp.topMargin = -(int) (6 * d);
                rlp.rightMargin = -(int) (6 * d);
                remove.setLayoutParams(rlp);
                remove.setText("×");
                remove.setTextColor(Color.WHITE);
                remove.setTextSize(12f);
                remove.setPadding(0, 0, 0, 0);
                remove.setMinWidth(0); remove.setMinHeight(0);
                remove.setMinimumWidth(0); remove.setMinimumHeight(0);
                GradientDrawable rbg = new GradientDrawable();
                rbg.setShape(GradientDrawable.OVAL);
                rbg.setColor(0xFFFF4444);
                remove.setBackground(rbg);
                remove.setOnClickListener(v -> { photoUris.remove(idx); refreshPhotoUI(); });

                thumbWrap.addView(thumb);
                thumbWrap.addView(remove);
                layoutExtraThumbs.addView(thumbWrap);
            }
        } else {
            layoutExtraThumbs.setVisibility(View.GONE);
        }

        if (hasPhotos && photoUris.size() < MAX_PHOTOS) {
            tvAddMore.setVisibility(View.VISIBLE);
            tvAddMore.setText("➕ Add another photo (" + photoUris.size() + "/" + MAX_PHOTOS + ")");
        } else {
            tvAddMore.setVisibility(View.GONE);
        }
    }

    void submit() {
        String title = etItemName.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String loc = etLocation.getText().toString().trim();
        String cont = etContact.getText().toString().trim();
        if (title.isEmpty()) { etItemName.setError("Required!"); return; }
        if (desc.isEmpty()) { etDescription.setError("Required!"); return; }
        if (loc.isEmpty()) { etLocation.setError("Required!"); return; }
        if (cont.isEmpty()) { etContact.setError("Required!"); return; }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        if (!photoUris.isEmpty()) {
            // Cover photo — same field/format as before, so anything reading only
            // imageUrl (older code, the web card view) keeps working unchanged.
            String coverB64 = ImageUtils.encodeImageToBase64(this, photoUris.get(0), 600, 55);
            Map<String, String> imagesMap = null;
            if (photoUris.size() > 1) {
                imagesMap = new LinkedHashMap<>();
                for (int i = 0; i < photoUris.size(); i++) {
                    String b64 = ImageUtils.encodeImageToBase64(this, photoUris.get(i), 500, 50);
                    if (b64 != null) imagesMap.put(String.valueOf(i), b64);
                }
            }
            save(uid, sdf, coverB64 != null ? coverB64 : "", imagesMap);
        } else {
            save(uid, sdf, "", null);
        }
    }

    void save(String uid, SimpleDateFormat sdf, String imgUrl, Map<String, String> imagesMap) {
        FirebaseDatabase.getInstance().getReference().child("users").child(uid).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        String userName = snap.exists() ? snap.getValue(String.class) : "User";
                        Map<String, Object> item = new HashMap<>();
                        item.put("title", etItemName.getText().toString().trim());
                        item.put("description", etDescription.getText().toString().trim());
                        item.put("category", spinnerCategory.getSelectedItem().toString());
                        item.put("location", etLocation.getText().toString().trim());
                        if (pickedLat != null) item.put("latitude", pickedLat);
                        if (pickedLng != null) item.put("longitude", pickedLng);
                        item.put("date", sdf.format(selectedDate.getTime()));
                        item.put("contact", etContact.getText().toString().trim());
                        item.put("imageUrl", imgUrl);
                        if (imagesMap != null) item.put("images", imagesMap);
                        item.put("ocrText", ocrText != null ? ocrText : "");
                        item.put("labelsText", labelsText != null ? labelsText : "");
                        item.put("verificationQuestion", etVerificationQuestion.getText().toString().trim());
                        item.put("verificationAnswer", etVerificationAnswer.getText().toString().trim());
                        item.put("type", "found");
                        item.put("userId", uid);
                        item.put("userName", userName);
                        item.put("timestamp", System.currentTimeMillis());

                        FirebaseDatabase.getInstance().getReference().child("items").push()
                                .setValue(item).addOnCompleteListener(t -> {
                                    if (t.isSuccessful()) {
                                        Toast.makeText(ReportFoundActivity.this, "Found item reported!", Toast.LENGTH_SHORT).show();
                                        finish();
                                    } else {
                                        String reason = t.getException() != null ? t.getException().getMessage() : "Unknown error";
                                        Toast.makeText(ReportFoundActivity.this, "Error: " + reason, Toast.LENGTH_LONG).show();
                                        btnSubmit.setEnabled(true);
                                        btnSubmit.setText("Submit Found Report");
                                    }
                                });
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }
}
