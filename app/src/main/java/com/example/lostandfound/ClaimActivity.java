package com.example.lostandfound;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class ClaimActivity extends AppCompatActivity {

    EditText etClaimReason, etOwnershipProof, etVerificationAnswer;
    Button btnSubmitClaim, btnUploadProof, btnBack;
    TextView tvItemTitle, tvClaimStatus, tvVerificationQuestion;
    LinearLayout layoutProofPreview, layoutVerificationQuestion;

    // ---- multi-photo state (up to 3 proof photos) ----
    LinearLayout layoutProofThumbs;
    static final int MAX_PROOF_PHOTOS = 3;
    final ArrayList<Uri> proofUris = new ArrayList<>();

    String itemId, itemTitle, itemOwnerId;
    String verificationQuestion = "";
    String realVerificationAnswer = ""; // the finder's real secret answer — the actual thing we should check against
    String realOcrText = ""; // fetched fresh from the item, never shown to the claimant
    private static final int PICK_IMAGE = 101;
    String currentUserId;
    boolean itemAlreadyResolved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claim);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        itemId = getIntent().getStringExtra("ITEM_ID");
        itemTitle = getIntent().getStringExtra("ITEM_TITLE");
        itemOwnerId = getIntent().getStringExtra("ITEM_OWNER_ID");

        etClaimReason = findViewById(R.id.etClaimReason);
        etOwnershipProof = findViewById(R.id.etOwnershipProof);
        etVerificationAnswer = findViewById(R.id.etVerificationAnswer);
        btnSubmitClaim = findViewById(R.id.btnSubmitClaim);
        btnUploadProof = findViewById(R.id.btnUploadProof);
        btnBack = findViewById(R.id.btnBack);
        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvClaimStatus = findViewById(R.id.tvClaimStatus);
        tvVerificationQuestion = findViewById(R.id.tvVerificationQuestion);
        layoutProofPreview = findViewById(R.id.layoutProofPreview);
        layoutVerificationQuestion = findViewById(R.id.layoutVerificationQuestion);
        layoutProofThumbs = findViewById(R.id.layoutProofThumbs);

        tvItemTitle.setText(itemTitle != null ? itemTitle : "Item");
        btnBack.setOnClickListener(v -> finish());

        loadItemVerificationDetails();
        checkExistingClaim();

        btnUploadProof.setOnClickListener(v -> {
            if (proofUris.size() >= MAX_PROOF_PHOTOS) {
                Toast.makeText(this, "You can add up to " + MAX_PROOF_PHOTOS + " proof photos.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
            startActivityForResult(i, PICK_IMAGE);
        });

        btnSubmitClaim.setOnClickListener(v -> submitClaim());
        refreshProofUI();
    }

    void loadItemVerificationDetails() {
        if (itemId == null) return;
        FirebaseDatabase.getInstance().getReference().child("items").child(itemId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        verificationQuestion = snap.child("verificationQuestion").getValue(String.class);
                        if (verificationQuestion == null) verificationQuestion = "";
                        realVerificationAnswer = snap.child("verificationAnswer").getValue(String.class);
                        if (realVerificationAnswer == null) realVerificationAnswer = "";
                        realOcrText = snap.child("ocrText").getValue(String.class);
                        if (realOcrText == null) realOcrText = "";
                        if (!verificationQuestion.isEmpty()) {
                            layoutVerificationQuestion.setVisibility(View.VISIBLE);
                            tvVerificationQuestion.setText(verificationQuestion);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    void checkExistingClaim() {
        FirebaseDatabase.getInstance().getReference().child("claims")
                .orderByChild("itemId").equalTo(itemId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        boolean myClaimExists = false;
                        String myStatus = "";
                        for (DataSnapshot s : snap.getChildren()) {
                            String status = s.child("status").getValue(String.class);
                            String claimant = s.child("claimantId").getValue(String.class);

                            if (status != null && status.contains("Approved")) {
                                itemAlreadyResolved = true;
                            }
                            if (currentUserId.equals(claimant)) {
                                myClaimExists = true;
                                myStatus = status != null ? status : "⏳ Pending";
                            }
                        }

                        if (itemAlreadyResolved) {
                            tvClaimStatus.setVisibility(View.VISIBLE);
                            tvClaimStatus.setText("This item has already been claimed and approved by someone else.");
                            btnSubmitClaim.setEnabled(false);
                            btnSubmitClaim.setText("Already Resolved");
                        } else if (myClaimExists) {
                            tvClaimStatus.setVisibility(View.VISIBLE);
                            tvClaimStatus.setText("Your claim status: " + myStatus);
                            btnSubmitClaim.setEnabled(false);
                            btnSubmitClaim.setText("Already Claimed");
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null && data.getData() != null) {
            proofUris.add(data.getData());
            refreshProofUI();
        }
    }

    // Rebuilds the proof-photo thumbnail strip (flat list, no "cover" concept —
    // any of these photos can be removed independently).
    void refreshProofUI() {
        layoutProofPreview.setVisibility(proofUris.isEmpty() ? View.GONE : View.VISIBLE);
        layoutProofThumbs.removeAllViews();
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < proofUris.size(); i++) {
            final int idx = i;
            FrameLayout thumbWrap = new FrameLayout(this);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams((int) (84 * d), (int) (84 * d));
            wlp.setMarginEnd((int) (10 * d));
            thumbWrap.setLayoutParams(wlp);

            ImageView thumb = new ImageView(this);
            thumb.setLayoutParams(new FrameLayout.LayoutParams((int) (84 * d), (int) (84 * d)));
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setImageURI(proofUris.get(idx));
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
            remove.setOnClickListener(v -> { proofUris.remove(idx); refreshProofUI(); });

            thumbWrap.addView(thumb);
            thumbWrap.addView(remove);
            layoutProofThumbs.addView(thumbWrap);
        }

        btnUploadProof.setText(proofUris.isEmpty()
                ? "📷 Upload Receipt / Photo"
                : "📷 Add another (" + proofUris.size() + "/" + MAX_PROOF_PHOTOS + ")");
    }

    void submitClaim() {
        if (itemAlreadyResolved) {
            Toast.makeText(this, "This item has already been claimed by someone else.", Toast.LENGTH_LONG).show();
            return;
        }

        String reason = etClaimReason.getText().toString().trim();
        String proof = etOwnershipProof.getText().toString().trim();
        String verificationAnswer = etVerificationAnswer.getText().toString().trim();

        if (reason.isEmpty()) { etClaimReason.setError("Please explain your claim!"); return; }
        if (!verificationQuestion.isEmpty() && verificationAnswer.isEmpty()) {
            etVerificationAnswer.setError("Please answer the verification question!");
            return;
        }

        btnSubmitClaim.setEnabled(false);

        String compareTarget = !realVerificationAnswer.isEmpty() ? realVerificationAnswer : realOcrText;

        if (!compareTarget.isEmpty() && !verificationAnswer.isEmpty()) {
            btnSubmitClaim.setText("🤖 AI checking answer...");
            FraudCheckHelper.checkMatch(verificationAnswer, compareTarget, new FraudCheckHelper.MatchCallback() {
                @Override public void onResult(double matchPercentage, String verdict) {
                    proceedWithSubmit(reason, proof, verificationAnswer, matchPercentage, verdict);
                }
                @Override public void onError(String error) {
                    double fallbackScore = MLHelper.calculateTextSimilarity(verificationAnswer, compareTarget) * 100.0;
                    proceedWithSubmit(reason, proof, verificationAnswer, fallbackScore, "Estimated (AI check unavailable)");
                }
            });
        } else {
            proceedWithSubmit(reason, proof, verificationAnswer, 0.0, "");
        }
    }

    void proceedWithSubmit(String reason, String proof, String verificationAnswer, double matchPercentage, String verdict) {
        btnSubmitClaim.setText("Submitting...");
        if (!proofUris.isEmpty()) {
            String coverB64 = ImageUtils.encodeImageToBase64(this, proofUris.get(0), 600, 55);
            Map<String, String> imagesMap = null;
            if (proofUris.size() > 1) {
                imagesMap = new LinkedHashMap<>();
                for (int i = 0; i < proofUris.size(); i++) {
                    String b64 = ImageUtils.encodeImageToBase64(this, proofUris.get(i), 500, 50);
                    if (b64 != null) imagesMap.put(String.valueOf(i), b64);
                }
            }
            saveClaim(reason, proof, verificationAnswer, matchPercentage, verdict,
                    coverB64 != null ? coverB64 : "", imagesMap);
        } else {
            saveClaim(reason, proof, verificationAnswer, matchPercentage, verdict, "", null);
        }
    }

    void saveClaim(String reason, String proof, String verificationAnswer, double matchPercentage, String verdict,
                   String proofImageUrl, Map<String, String> proofImagesMap) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
        dbRef.child("users").child(currentUserId).child("name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        String claimantName = snap.exists() ? snap.getValue(String.class) : "User";
                        Map<String, Object> claim = new HashMap<>();
                        claim.put("itemId", itemId);
                        claim.put("itemTitle", itemTitle);
                        claim.put("claimantId", currentUserId);
                        claim.put("claimantName", claimantName);
                        claim.put("reason", reason);
                        claim.put("ownershipProof", proof);
                        claim.put("verificationAnswer", verificationAnswer);
                        claim.put("hiddenMatchScore", matchPercentage / 100.0);
                        claim.put("aiMatchPercentage", matchPercentage);
                        claim.put("aiVerdict", verdict != null ? verdict : "");
                        claim.put("proofImageUrl", proofImageUrl);
                        if (proofImagesMap != null) claim.put("proofImages", proofImagesMap);
                        claim.put("status", "⏳ Pending");
                        claim.put("timestamp", System.currentTimeMillis());

                        dbRef.child("claims").push().setValue(claim).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                FCMHelper.notifyNewClaim(itemOwnerId, itemId, itemTitle, claimantName);
                                Toast.makeText(ClaimActivity.this, "Claim submitted!", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(ClaimActivity.this, "Error!", Toast.LENGTH_SHORT).show();
                                btnSubmitClaim.setEnabled(true);
                                btnSubmitClaim.setText("Submit Claim");
                            }
                        });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }
}
