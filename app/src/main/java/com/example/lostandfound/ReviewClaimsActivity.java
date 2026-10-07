package com.example.lostandfound;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class ReviewClaimsActivity extends AppCompatActivity {

    TextView btnBack, tvClaimCount, tvNoClaims;
    ListView listClaimsReview;
    DatabaseReference dbRef;
    String itemId, itemTitle;
    ArrayList<Map<String, Object>> claimsList = new ArrayList<>();
    ArrayList<String> claimKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review_claims);

        dbRef = FirebaseDatabase.getInstance().getReference();
        itemId = getIntent().getStringExtra("ITEM_ID");
        itemTitle = getIntent().getStringExtra("ITEM_TITLE");

        btnBack = findViewById(R.id.btnBack);
        tvClaimCount = findViewById(R.id.tvClaimCount);
        tvNoClaims = findViewById(R.id.tvNoClaims);
        listClaimsReview = findViewById(R.id.listClaimsReview);
        btnBack.setOnClickListener(v -> finish());

        loadClaims();
    }

    void loadClaims() {
        dbRef.child("claims").orderByChild("itemId").equalTo(itemId)
                .addValueEventListener(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot snap) {
                        claimsList.clear();
                        claimKeys.clear();
                        for (DataSnapshot s : snap.getChildren()) {
                            Map<String, Object> claim = new HashMap<>();
                            claim.put("claimantName", s.child("claimantName").getValue(String.class));
                            claim.put("claimantId", s.child("claimantId").getValue(String.class));
                            claim.put("reason", s.child("reason").getValue(String.class));
                            claim.put("verificationAnswer", s.child("verificationAnswer").getValue(String.class));
                            Double hiddenScore = s.child("hiddenMatchScore").getValue(Double.class);
                            claim.put("hiddenMatchScore", hiddenScore != null ? hiddenScore : 0.0);
                            claim.put("proofImageUrl", s.child("proofImageUrl").getValue(String.class));
                            claim.put("proofImages", Item.readImagesMap(s, "proofImages"));
                            claim.put("status", s.child("status").getValue(String.class));
                            claim.put("timestamp", s.child("timestamp").getValue(Long.class));
                            claimsList.add(0, claim);
                            claimKeys.add(0, s.getKey());
                        }
                        tvClaimCount.setText(claimsList.size() + " claim(s) for \"" + (itemTitle != null ? itemTitle : "this item") + "\"");
                        renderClaims();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    void renderClaims() {
        if (claimsList.isEmpty()) {
            tvNoClaims.setVisibility(View.VISIBLE);
            listClaimsReview.setVisibility(View.GONE);
            return;
        }
        tvNoClaims.setVisibility(View.GONE);
        listClaimsReview.setVisibility(View.VISIBLE);

        boolean anyApproved = false;
        for (Map<String, Object> c : claimsList) {
            String status = (String) c.get("status");
            if (status != null && status.contains("Approved")) anyApproved = true;
        }
        final boolean itemResolved = anyApproved;

        ArrayAdapter<Map<String, Object>> adapter = new ArrayAdapter<Map<String, Object>>(this, R.layout.item_claim_review, claimsList) {
            @NonNull @Override
            public View getView(int pos, @Nullable View cv, @NonNull ViewGroup parent) {
                if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_claim_review, parent, false);
                Map<String, Object> claim = claimsList.get(pos);
                String key = claimKeys.get(pos);

                String status = (String) claim.get("status");
                if (status == null) status = "⏳ Pending";

                ((TextView) cv.findViewById(R.id.tvClaimantName)).setText((String) claim.get("claimantName"));
                ((TextView) cv.findViewById(R.id.tvClaimReviewReason)).setText("💬 " + claim.get("reason"));

                TextView tvStatus = cv.findViewById(R.id.tvClaimReviewStatus);
                tvStatus.setText(status);
                if (status.contains("Approved")) tvStatus.setTextColor(0xFF2ECC71);
                else if (status.contains("Rejected")) tvStatus.setTextColor(0xFFFF4444);
                else tvStatus.setTextColor(0xFF6C63FF);

                String verificationAnswer = (String) claim.get("verificationAnswer");
                LinearLayout layoutHiddenMatch = cv.findViewById(R.id.layoutHiddenMatch);
                if (verificationAnswer != null && !verificationAnswer.isEmpty()) {
                    layoutHiddenMatch.setVisibility(View.VISIBLE);
                    ((TextView) cv.findViewById(R.id.tvClaimVerificationAnswer)).setText(verificationAnswer);
                    TextView tvHint = cv.findViewById(R.id.tvHiddenMatchIndicator);
                    double score = (double) claim.get("hiddenMatchScore");
                    if (score >= 0.5) {
                        tvHint.setText("✅ Strong match with hidden item details");
                        tvHint.setTextColor(0xFF2ECC71);
                    } else if (score > 0.15) {
                        tvHint.setText("⚠️ Partial match — verify carefully");
                        tvHint.setTextColor(0xFFF2A65A);
                    } else {
                        tvHint.setText("ℹ️ No strong match found — ask more questions before approving");
                        tvHint.setTextColor(0xFF9B8EC4);
                    }
                } else {
                    layoutHiddenMatch.setVisibility(View.GONE);
                }

                String proofUrl = (String) claim.get("proofImageUrl");
                ImageView imgProof = cv.findViewById(R.id.imgClaimProof);
                @SuppressWarnings("unchecked")
                Map<String, String> proofImages = (Map<String, String>) claim.get("proofImages");
                HorizontalScrollView scrollThumbs = cv.findViewById(R.id.scrollClaimProofThumbs);
                LinearLayout layoutThumbs = cv.findViewById(R.id.layoutClaimProofThumbs);

                if (proofImages != null && proofImages.size() > 1) {
                    imgProof.setVisibility(View.GONE);
                    scrollThumbs.setVisibility(View.VISIBLE);
                    layoutThumbs.removeAllViews();

                    List<String> keys = new ArrayList<>(proofImages.keySet());
                    keys.sort((a, b) -> {
                        try { return Integer.parseInt(a) - Integer.parseInt(b); }
                        catch (NumberFormatException e) { return a.compareTo(b); }
                    });
                    float d = getResources().getDisplayMetrics().density;
                    for (String k : keys) {
                        ImageView thumb = new ImageView(ReviewClaimsActivity.this);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (100 * d), (int) (100 * d));
                        lp.setMarginEnd((int) (8 * d));
                        thumb.setLayoutParams(lp);
                        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        ImageUtils.loadItemImage(ReviewClaimsActivity.this, proofImages.get(k), thumb, R.drawable.chip_default);
                        layoutThumbs.addView(thumb);
                    }
                } else {
                    scrollThumbs.setVisibility(View.GONE);
                    if (proofUrl != null && !proofUrl.isEmpty()) {
                        imgProof.setVisibility(View.VISIBLE);
                        ImageUtils.loadItemImage(ReviewClaimsActivity.this, proofUrl, imgProof, R.drawable.chip_default);
                    } else {
                        imgProof.setVisibility(View.GONE);
                    }
                }

                Long ts = (Long) claim.get("timestamp");
                String dateStr = ts != null ? new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(ts)) : "";
                ((TextView) cv.findViewById(R.id.tvClaimReviewDate)).setText(dateStr);

                LinearLayout layoutActions = cv.findViewById(R.id.layoutClaimReviewActions);
                if (!status.equals("⏳ Pending") || itemResolved) {
                    layoutActions.setVisibility(View.GONE);
                } else {
                    layoutActions.setVisibility(View.VISIBLE);
                    cv.findViewById(R.id.btnApproveClaim).setOnClickListener(v -> confirmApprove(key, claim));
                    cv.findViewById(R.id.btnRejectClaim).setOnClickListener(v -> confirmReject(key, claim));
                }

                return cv;
            }
        };
        listClaimsReview.setAdapter(adapter);
    }

    void confirmApprove(String claimKey, Map<String, Object> claim) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Approve Claim")
                .setMessage("Approve " + claim.get("claimantName") + "'s claim for \"" + itemTitle + "\"?\n\n" +
                        "This will automatically reject all other pending claims on this item. " +
                        "Please verify their identity in person before handing over the item.")
                .setPositiveButton("Approve", (d, w) -> approveClaim(claimKey, (String) claim.get("claimantId")))
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmReject(String claimKey, Map<String, Object> claim) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Reject Claim")
                .setMessage("Reject " + claim.get("claimantName") + "'s claim?")
                .setPositiveButton("Reject", (d, w) -> rejectClaim(claimKey, (String) claim.get("claimantId")))
                .setNegativeButton("Cancel", null)
                .show();
    }

    void approveClaim(String approvedKey, String approvedClaimantId) {
        dbRef.child("claims").child(approvedKey).child("status").setValue("✅ Approved");
        FCMHelper.notifyClaimResult(approvedClaimantId, itemId, itemTitle, true);

        // Auto-reject every other pending claim on this same item — fraud
        // protection: only one claim can ever "win" a found item.
        for (int i = 0; i < claimKeys.size(); i++) {
            String key = claimKeys.get(i);
            if (key.equals(approvedKey)) continue;
            Map<String, Object> other = claimsList.get(i);
            String status = (String) other.get("status");
            if (status == null || status.equals("⏳ Pending")) {
                dbRef.child("claims").child(key).child("status").setValue("❌ Rejected");
                String otherClaimantId = (String) other.get("claimantId");
                if (otherClaimantId != null) FCMHelper.notifyClaimResult(otherClaimantId, itemId, itemTitle, false);
            }
        }

        // Reflect the resolution on the item itself too
        Map<String, Object> update = new HashMap<>();
        update.put("status", "🎉 Returned to Owner");
        update.put("statusUpdatedAt", System.currentTimeMillis());
        dbRef.child("items").child(itemId).updateChildren(update);

        Toast.makeText(this, "✅ Claim approved. Please arrange a safe handover.", Toast.LENGTH_LONG).show();
    }

    void rejectClaim(String claimKey, String claimantId) {
        dbRef.child("claims").child(claimKey).child("status").setValue("❌ Rejected");
        if (claimantId != null) FCMHelper.notifyClaimResult(claimantId, itemId, itemTitle, false);
        Toast.makeText(this, "Claim rejected.", Toast.LENGTH_SHORT).show();
    }
}
