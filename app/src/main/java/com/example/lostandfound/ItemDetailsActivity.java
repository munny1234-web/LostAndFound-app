package com.example.lostandfound;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class ItemDetailsActivity extends AppCompatActivity {

    TextView tvType, tvCategory, tvTitle, tvDescription, tvLocation, tvDate, tvReward, tvMatches, btnBack, tvStatusBadge, tvPhotoCount;
    Button btnChat, btnCall, btnStatus, btnReport, btnClaim, btnShare, btnReviewClaims;
    ImageView imgItem;
    LinearLayout layoutMatches, layoutMatchCards, layoutGallery;
    HorizontalScrollView scrollGallery;
    ProgressBar progressMatches;
    Item item;
    String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_details);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        tvType = findViewById(R.id.tvType);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvPhotoCount = findViewById(R.id.tvPhotoCount);
        scrollGallery = findViewById(R.id.scrollGallery);
        layoutGallery = findViewById(R.id.layoutGallery);
        tvCategory = findViewById(R.id.tvCategory);
        tvTitle = findViewById(R.id.tvTitle);
        tvDescription = findViewById(R.id.tvDescription);
        tvLocation = findViewById(R.id.tvLocation);
        tvDate = findViewById(R.id.tvDate);
        tvReward = findViewById(R.id.tvReward);
        tvMatches = findViewById(R.id.tvMatches);
        btnBack = findViewById(R.id.btnBack);
        btnChat = findViewById(R.id.btnChat);
        btnCall = findViewById(R.id.btnCall);
        btnStatus = findViewById(R.id.btnStatus);
        btnReport = findViewById(R.id.btnReport);
        btnClaim = findViewById(R.id.btnClaim);
        btnReviewClaims = findViewById(R.id.btnReviewClaims);
        btnShare = findViewById(R.id.btnShare);
        imgItem = findViewById(R.id.imgItem);
        layoutMatches = findViewById(R.id.layoutMatches);
        layoutMatchCards = findViewById(R.id.layoutMatchCards);
        progressMatches = findViewById(R.id.progressMatches);

        item = (Item) getIntent().getSerializableExtra("ITEM");
        if (item == null) { finish(); return; }

        boolean isLost = "lost".equals(item.type);
        tvType.setText(isLost ? "LOST" : "FOUND");
        tvType.setTextColor(isLost ? 0xFFFF4444 : 0xFF2ECC71);
        tvType.setBackgroundResource(isLost ? R.drawable.tag_lost : R.drawable.tag_found);
        tvCategory.setText(item.category);
        tvTitle.setText(item.title);
        tvDescription.setText(item.description);
        tvLocation.setText(item.location);

        TextView tvViewOnMap = findViewById(R.id.tvViewOnMap);
        if (item.latitude != null && item.longitude != null) {
            tvViewOnMap.setVisibility(View.VISIBLE);
            tvViewOnMap.setOnClickListener(v -> {
                String label = item.title != null ? item.title : "Item location";
                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + item.latitude + "," + item.longitude + "(" + Uri.encode(label) + ")");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Toast.makeText(this, "No map app found to open this location.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        tvDate.setText(item.date);
        tvReward.setText(item.reward != null && !item.reward.isEmpty() ? "🎁 " + item.reward : "");

        // Live status badge — everyone viewing this item sees the current status
        // in real time, not just the owner who set it.
        if (item.id != null) {
            FirebaseDatabase.getInstance().getReference()
                    .child("items").child(item.id).child("status")
                    .addValueEventListener(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snap) {
                            String status = snap.getValue(String.class);
                            boolean resolved = status != null &&
                                    (status.contains("Found") || status.contains("Returned"));
                            if (resolved) {
                                tvStatusBadge.setText(status);
                                tvStatusBadge.setVisibility(View.VISIBLE);
                            } else {
                                tvStatusBadge.setVisibility(View.GONE);
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
        }

        if (item.images != null && item.images.size() > 1) {
            // Multiple photos — show a swipeable gallery instead of the single image.
            imgItem.setVisibility(View.GONE);
            scrollGallery.setVisibility(View.VISIBLE);
            layoutGallery.removeAllViews();

            java.util.List<String> keys = new java.util.ArrayList<>(item.images.keySet());
            keys.sort((a, b) -> {
                try { return Integer.parseInt(a) - Integer.parseInt(b); }
                catch (NumberFormatException e) { return a.compareTo(b); }
            });
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int heightPx = (int) (240 * getResources().getDisplayMetrics().density);
            for (String k : keys) {
                ImageView photo = new ImageView(this);
                photo.setLayoutParams(new LinearLayout.LayoutParams(screenWidth, heightPx));
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ImageUtils.loadItemImage(this, item.images.get(k), photo, R.drawable.chip_default);
                layoutGallery.addView(photo);
            }
            tvPhotoCount.setVisibility(View.VISIBLE);
            tvPhotoCount.setText("📸 " + keys.size() + " photos — swipe");
        } else if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            ImageUtils.loadItemImage(this, item.imageUrl, imgItem, R.drawable.chip_default);
        }

        btnBack.setOnClickListener(v -> finish());

        boolean isOwner = currentUserId.equals(item.userId);

        // Show/hide buttons based on ownership
        btnStatus.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        btnReport.setVisibility(isOwner ? View.GONE : View.VISIBLE);
        btnClaim.setVisibility(!isOwner && "found".equals(item.type) ? View.VISIBLE : View.GONE);
        btnReviewClaims.setVisibility(isOwner && "found".equals(item.type) ? View.VISIBLE : View.GONE);
        btnReviewClaims.setOnClickListener(v -> {
            Intent i = new Intent(this, ReviewClaimsActivity.class);
            i.putExtra("ITEM_ID", item.id);
            i.putExtra("ITEM_TITLE", item.title);
            startActivity(i);
        });

        btnStatus.setOnClickListener(v -> {
            Intent i = new Intent(this, ItemStatusActivity.class);
            i.putExtra("ITEM_ID", item.id);
            i.putExtra("USER_ID", item.userId);
            i.putExtra("ITEM_TITLE", item.title);
            startActivity(i);
        });

        btnClaim.setOnClickListener(v -> {
            Intent i = new Intent(this, ClaimActivity.class);
            i.putExtra("ITEM_ID", item.id);
            i.putExtra("ITEM_TITLE", item.title);
            i.putExtra("ITEM_OWNER_ID", item.userId);
            startActivity(i);
        });

        btnShare.setOnClickListener(v -> sharePost());
        btnReport.setOnClickListener(v -> showReportDialog());

        btnChat.setOnClickListener(v -> {
            if (isOwner) { Toast.makeText(this, "This is your own post!", Toast.LENGTH_SHORT).show(); return; }
            FirebaseDatabase.getInstance().getReference()
                    .child("users").child(currentUserId).child("name")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snap) {
                            String myName = snap.exists() ? snap.getValue(String.class) : "Someone";
                            FCMHelper.notifyNewMessage(item.userId, currentUserId, myName, item.id, item.title);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError e) {}
                    });
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("ITEM_ID", item.id);
            i.putExtra("ITEM_TITLE", item.title);
            i.putExtra("OTHER_USER_ID", item.userId);
            i.putExtra("OTHER_USER_NAME", item.userName);
            startActivity(i);
        });

        btnCall.setOnClickListener(v -> {
            if (item.contact != null && !item.contact.isEmpty())
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.contact)));
        });

        findSmartMatches();
    }

    void sharePost() {
        boolean isLost = "lost".equals(item.type);
        String shareText =
                "🔍 " + (isLost ? "LOST ITEM" : "FOUND ITEM") + "\n\n" +
                        "📦 " + item.title + "\n" +
                        "📂 Category: " + item.category + "\n" +
                        "📍 Location: " + item.location + "\n" +
                        "📅 Date: " + item.date + "\n" +
                        (item.description != null ? "📝 " + item.description + "\n" : "") +
                        (item.reward != null && !item.reward.isEmpty() ? "🎁 Reward: " + item.reward + "\n" : "") +
                        "\n📞 Contact: " + item.contact + "\n\n" +
                        "Posted on Lost & Found App";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    void showReportDialog() {
        String[] reasons = {"Fake Post", "Spam", "Inappropriate Content", "Already Resolved", "Other"};
        new AlertDialog.Builder(this)
                .setTitle("Report Post")
                .setItems(reasons, (dialog, which) -> {
                    Map<String, Object> report = new HashMap<>();
                    report.put("itemId", item.id);
                    report.put("itemTitle", item.title);
                    report.put("reason", reasons[which]);
                    report.put("reportedBy", currentUserId);
                    report.put("status", "pending");
                    report.put("timestamp", System.currentTimeMillis());
                    FirebaseDatabase.getInstance().getReference().child("reports").push()
                            .setValue(report).addOnCompleteListener(task ->
                                    Toast.makeText(this, "Report submitted!", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void findSmartMatches() {
        progressMatches.setVisibility(View.VISIBLE);
        tvMatches.setText("🤖 AI is finding matches...");
        FirebaseDatabase.getInstance().getReference().child("items")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        List<Item> allItems = new ArrayList<>();
                        for (DataSnapshot s : snap.getChildren()) {
                            Item i = new Item();
                            i.id = s.getKey();
                            i.title = s.child("title").getValue(String.class);
                            i.description = s.child("description").getValue(String.class);
                            i.category = s.child("category").getValue(String.class);
                            i.location = s.child("location").getValue(String.class);
                            i.date = s.child("date").getValue(String.class);
                            i.contact = s.child("contact").getValue(String.class);
                            i.reward = s.child("reward").getValue(String.class);
                            i.type = s.child("type").getValue(String.class);
                            i.imageUrl = s.child("imageUrl").getValue(String.class);
                            i.ocrText = s.child("ocrText").getValue(String.class);
                            i.latitude = s.child("latitude").getValue(Double.class);
                            i.longitude = s.child("longitude").getValue(Double.class);
                            i.labelsText = s.child("labelsText").getValue(String.class);
                            i.userId = s.child("userId").getValue(String.class);
                            i.userName = s.child("userName").getValue(String.class);
                            Long mts = s.child("timestamp").getValue(Long.class);
                            i.timestamp = mts != null ? mts : 0;
                            allItems.add(i);
                        }
                        List<Item> matches = MLHelper.findSmartMatches(item, allItems, 0.15);
                        progressMatches.setVisibility(View.GONE);
                        layoutMatchCards.removeAllViews();
                        if (matches.isEmpty()) {
                            tvMatches.setText("No matches found yet.\nCheck back later!");
                            return;
                        }
                        notifyOwnerOnceAboutMatch();
                        tvMatches.setText("Found " + matches.size() + " possible match(es):");
                        int count = Math.min(matches.size(), 5);
                        for (int i = 0; i < count; i++) {
                            Item match = matches.get(i);
                            View card = getLayoutInflater().inflate(R.layout.item_match_card, layoutMatchCards, false);
                            ((TextView) card.findViewById(R.id.tvMatchScore)).setText(MLHelper.getSimilarityText(match.matchScore));
                            ((TextView) card.findViewById(R.id.tvMatchTitle)).setText(match.title != null ? match.title : "(untitled)");
                            ((TextView) card.findViewById(R.id.tvMatchMeta)).setText(
                                    "📂 " + (match.category != null ? match.category : "") +
                                            "   📍 " + (match.location != null ? match.location : ""));
                            ImageView imgThumb = card.findViewById(R.id.imgMatchThumb);
                            ImageUtils.loadItemImage(ItemDetailsActivity.this, match.imageUrl, imgThumb, R.drawable.chip_default);
                            card.setOnClickListener(v -> {
                                Intent i2 = new Intent(ItemDetailsActivity.this, ItemDetailsActivity.class);
                                i2.putExtra("ITEM", match);
                                startActivity(i2);
                            });
                            layoutMatchCards.addView(card);
                        }
                        if (!matches.isEmpty() && matches.get(0).imageUrl != null
                                && !matches.get(0).imageUrl.isEmpty()
                                && item.imageUrl != null && !item.imageUrl.isEmpty()) {
                            compareImages(matches.get(0));
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {}
                });
    }

    // Notify the item owner about a match only once (not on every page view,
    // and never notify the owner about their own visit to their own post).
    void notifyOwnerOnceAboutMatch() {
        if (item.userId == null || item.id == null) return;
        boolean isOwner = currentUserId.equals(item.userId);
        if (isOwner) return; // don't self-notify when the owner views their own post

        DatabaseReference notifiedRef = FirebaseDatabase.getInstance().getReference()
                .child("items").child(item.id).child("matchNotified");
        notifiedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                Boolean already = snap.getValue(Boolean.class);
                if (already == null || !already) {
                    notifiedRef.setValue(true);
                    FCMHelper.notifyMatchFound(item.userId, item.id, item.title);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    void compareImages(Item matchItem) {
        Bitmap b1 = getBitmapForComparison(item.imageUrl);
        Bitmap b2 = getBitmapForComparison(matchItem.imageUrl);
        if (b1 != null && b2 != null) {
            double sim = MLHelper.calculateImageSimilarity(b1, b2);
            if (sim > 0.5) {
                String cur = tvMatches.getText().toString();
                tvMatches.setText("📸 Visual similarity: " + Math.round(sim * 100) + "%\n\n" + cur);
            }
        }
    }

    // Returns a Bitmap from either a Base64-encoded photo (new items) or, for
    // backward compatibility, decodes null for old Storage https:// URLs since
    // that would need an async network fetch (skipped here to keep this simple).
    Bitmap getBitmapForComparison(String imageData) {
        if (imageData == null || imageData.isEmpty() || imageData.startsWith("http")) return null;
        return ImageUtils.decodeBase64(imageData);
    }
}
