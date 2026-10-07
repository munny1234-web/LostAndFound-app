package com.example.lostandfound;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.IOException;
import java.util.*;

public class MLHelper {

    // ============================================
    // 0. TEXT (OCR) DETECTION — reads any visible text on the item's photo
    // (brand names, ID/serial numbers, name tags) as an extra matching signal.
    // ============================================
    public interface TextDetectCallback {
        void onResult(String detectedText);
        void onError(String error);
    }

    public static void detectTextInImage(Context context, Uri imageUri, TextDetectCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(result -> callback.onResult(result.getText()))
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
        } catch (IOException e) {
            callback.onError(e.getMessage());
        }
    }

    // ============================================
    // 1. IMAGE LABELING — Auto detect category
    // ============================================
    public interface ImageLabelCallback {
        void onResult(String suggestedCategory, List<String> allLabels);
        void onError(String error);
    }

    public static void detectImageLabels(Context context, Uri imageUri, ImageLabelCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            ImageLabelerOptions options = new ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.65f)
                    .build();
            ImageLabeler labeler = ImageLabeling.getClient(options);
            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        List<String> labelNames = new ArrayList<>();
                        for (ImageLabel label : labels) {
                            labelNames.add(label.getText() + " (" + Math.round(label.getConfidence() * 100) + "%)");
                        }
                        String category = mapLabelToCategory(labels);
                        callback.onResult(category, labelNames);
                    })
                    .addOnFailureListener(e -> callback.onError(e.getMessage()));
        } catch (IOException e) {
            callback.onError(e.getMessage());
        }
    }

    // Map ML labels to app categories
    private static String mapLabelToCategory(List<ImageLabel> labels) {
        Map<String, List<String>> categoryMap = new LinkedHashMap<>();
        categoryMap.put("Phone", Arrays.asList("Mobile phone", "Smartphone", "Telephone", "Communication Device", "Gadget", "Mobile device"));
        categoryMap.put("Electronics", Arrays.asList("Laptop", "Computer", "Tablet", "Camera", "Electronic device", "Technology", "Headphones", "Earphone", "Charger", "Cable", "Speaker"));
        categoryMap.put("Jewelry", Arrays.asList("Jewellery", "Ring", "Necklace", "Bracelet", "Watch", "Wristwatch", "Gold", "Gemstone", "Earrings", "Chain"));
        categoryMap.put("Wallet/Purse", Arrays.asList("Wallet", "Purse", "Handbag", "Leather", "Coin purse"));
        categoryMap.put("Keys", Arrays.asList("Key", "Keychain"));
        categoryMap.put("Documents", Arrays.asList("Document", "Paper", "Book", "Card", "Passport", "License", "Certificate", "Notebook", "Id card"));
        categoryMap.put("Bag", Arrays.asList("Backpack", "Luggage", "Suitcase", "Briefcase", "Tote bag"));
        categoryMap.put("Clothing", Arrays.asList("Clothing", "Shirt", "Jacket", "Shoe", "Hat", "Cap", "Sweater", "Scarf", "Glove"));
        categoryMap.put("Glasses", Arrays.asList("Glasses", "Sunglasses", "Eyewear", "Spectacles"));
        categoryMap.put("Bottle/Container", Arrays.asList("Bottle", "Water bottle", "Container", "Flask", "Thermos"));
        categoryMap.put("Umbrella", Arrays.asList("Umbrella"));
        categoryMap.put("Toy", Arrays.asList("Toy", "Doll", "Action figure", "Stuffed toy"));
        categoryMap.put("Sports Equipment", Arrays.asList("Ball", "Racket", "Bicycle", "Helmet", "Skateboard"));
        categoryMap.put("Musical Instrument", Arrays.asList("Guitar", "Instrument", "Violin", "Drum"));
        categoryMap.put("Pet", Arrays.asList("Dog", "Cat", "Animal", "Pet", "Bird", "Mammal"));

        // Score every category by summing the confidence of every label that
        // matches it, instead of returning on the FIRST match found (which
        // depended on map/label ordering and let a generic label like "Metal"
        // hijack the result before a more specific label like "Watch" was
        // even considered). The category with the highest total confidence wins.
        Map<String, Double> categoryScores = new HashMap<>();
        for (ImageLabel label : labels) {
            String labelText = label.getText().toLowerCase();
            for (Map.Entry<String, List<String>> entry : categoryMap.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (labelText.equals(keyword.toLowerCase()) || labelText.contains(keyword.toLowerCase())) {
                        categoryScores.merge(entry.getKey(), (double) label.getConfidence(), Double::sum);
                        break; // count this label once per category even if multiple keywords match
                    }
                }
            }
        }

        String bestCategory = "Other";
        double bestScore = 0.0;
        for (Map.Entry<String, Double> entry : categoryScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestCategory = entry.getKey();
            }
        }
        return bestCategory;
    }

    // ============================================
    // 2. TEXT SIMILARITY — Smart matching
    // ============================================
    public static double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        text1 = text1.toLowerCase().trim();
        text2 = text2.toLowerCase().trim();

        // TF-IDF based similarity
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) return 0.0;

        // Jaccard similarity
        double jaccard = (double) intersection.size() / union.size();

        // Bonus for exact substring match
        double substringBonus = 0.0;
        if (text1.contains(text2) || text2.contains(text1)) substringBonus = 0.2;

        return Math.min(1.0, jaccard + substringBonus);
    }

    // ============================================
    // 2b. SEMANTIC LABEL SIMILARITY — compares what ML Kit actually
    // recognized in each photo (e.g. "Wristwatch", "Metal", "Analog watch")
    // instead of raw pixel brightness. This is far more reliable than pixel
    // hashing because it reflects real object content, not lighting/angle.
    // ============================================
    private static Map<String, Double> parseLabels(String labelsText) {
        Map<String, Double> map = new HashMap<>();
        if (labelsText == null || labelsText.isEmpty()) return map;
        for (String part : labelsText.split(",")) {
            part = part.trim();
            int idx = part.lastIndexOf('(');
            if (idx > 0 && part.endsWith("%)")) {
                String name = part.substring(0, idx).trim().toLowerCase();
                String pctStr = part.substring(idx + 1, part.length() - 2).trim();
                try {
                    map.put(name, Double.parseDouble(pctStr) / 100.0);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public static double calculateLabelSimilarity(String labels1, String labels2) {
        Map<String, Double> m1 = parseLabels(labels1);
        Map<String, Double> m2 = parseLabels(labels2);
        if (m1.isEmpty() || m2.isEmpty()) return 0.0;

        Set<String> union = new HashSet<>(m1.keySet());
        union.addAll(m2.keySet());

        double overlapWeight = 0.0, totalWeight = 0.0;
        for (String label : union) {
            double c1 = m1.getOrDefault(label, 0.0);
            double c2 = m2.getOrDefault(label, 0.0);
            overlapWeight += Math.min(c1, c2);
            totalWeight += Math.max(c1, c2);
        }
        return totalWeight == 0.0 ? 0.0 : overlapWeight / totalWeight;
    }

    // Find best matches from a list of items
    public static List<Item> findSmartMatches(Item targetItem, List<Item> allItems, double threshold) {
        List<Item> matches = new ArrayList<>();
        String oppositeType = "lost".equals(targetItem.type) ? "found" : "lost";

        for (Item item : allItems) {
            if (!oppositeType.equals(item.type)) continue;
            if (item.id != null && item.id.equals(targetItem.id)) continue;

            double score = 0.0;

            // Title similarity (weight: 28%)
            if (targetItem.title != null && item.title != null) {
                score += calculateTextSimilarity(targetItem.title, item.title) * 0.28;
            }

            // Description similarity (weight: 24%)
            if (targetItem.description != null && item.description != null) {
                score += calculateTextSimilarity(targetItem.description, item.description) * 0.24;
            }

            // Category match (weight: 10%)
            if (targetItem.category != null && item.category != null &&
                    targetItem.category.equalsIgnoreCase(item.category)) {
                score += 0.10;
            }

            // Location similarity (weight: 6%)
            if (targetItem.location != null && item.location != null) {
                score += calculateTextSimilarity(targetItem.location, item.location) * 0.06;
            }

            // OCR text overlap (weight: 16%) — a strong signal: if the photo
            // shows a serial number, brand name, or printed name/ID that
            // matches between the two posts, that's rarely a coincidence.
            if (targetItem.ocrText != null && !targetItem.ocrText.isEmpty()
                    && item.ocrText != null && !item.ocrText.isEmpty()) {
                score += calculateTextSimilarity(targetItem.ocrText, item.ocrText) * 0.16;
            }

            // Semantic photo-label overlap (weight: 16%) — what ML Kit actually
            // recognized in each photo (much stronger than raw pixel comparison).
            if (targetItem.labelsText != null && !targetItem.labelsText.isEmpty()
                    && item.labelsText != null && !item.labelsText.isEmpty()) {
                score += calculateLabelSimilarity(targetItem.labelsText, item.labelsText) * 0.16;
            }

            item.matchScore = score;
            if (score >= threshold) matches.add(item);
        }

        // Sort by score descending
        matches.sort((a, b) -> Double.compare(b.matchScore, a.matchScore));
        return matches;
    }

    // ============================================
    // 3. IMAGE SIMILARITY — Visual matching
    // ============================================
    public static double calculateImageSimilarity(Bitmap bitmap1, Bitmap bitmap2) {
        if (bitmap1 == null || bitmap2 == null) return 0.0;

        // Two independent techniques, averaged together for robustness:
        // - Average hash: overall tone/brightness pattern across the WHOLE image
        // - Difference hash: brightness gradients between neighboring pixels,
        //   which stays stable even if lighting/exposure differs between photos
        double aHashSim = compareAverageHash(bitmap1, bitmap2);
        double dHashSim = compareDifferenceHash(bitmap1, bitmap2);
        return (aHashSim + dHashSim) / 2.0;
    }

    private static double compareAverageHash(Bitmap bitmap1, Bitmap bitmap2) {
        int size = 16; // 16x16 = 256 pixels -> a 256-bit fingerprint using the WHOLE image
        Bitmap b1 = Bitmap.createScaledBitmap(bitmap1, size, size, false);
        Bitmap b2 = Bitmap.createScaledBitmap(bitmap2, size, size, false);
        boolean[] hash1 = averageHashBits(b1, size);
        boolean[] hash2 = averageHashBits(b2, size);
        return bitSimilarity(hash1, hash2);
    }

    private static boolean[] averageHashBits(Bitmap bitmap, int size) {
        int[] pixels = new int[size * size];
        int total = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int gray = toGray(bitmap.getPixel(x, y));
                pixels[y * size + x] = gray;
                total += gray;
            }
        }
        int avg = total / pixels.length;
        boolean[] bits = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) bits[i] = pixels[i] >= avg;
        return bits;
    }

    // Difference hash: compares each pixel to its right neighbor's brightness,
    // capturing edges/gradients rather than raw brightness — more robust to
    // lighting differences between two separately-taken photos.
    private static double compareDifferenceHash(Bitmap bitmap1, Bitmap bitmap2) {
        int w = 17, h = 16; // one extra column so every row gives 16 left-right comparisons
        Bitmap b1 = Bitmap.createScaledBitmap(bitmap1, w, h, false);
        Bitmap b2 = Bitmap.createScaledBitmap(bitmap2, w, h, false);
        boolean[] hash1 = differenceHashBits(b1, w, h);
        boolean[] hash2 = differenceHashBits(b2, w, h);
        return bitSimilarity(hash1, hash2);
    }

    private static boolean[] differenceHashBits(Bitmap bitmap, int w, int h) {
        boolean[] bits = new boolean[(w - 1) * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            int prevGray = toGray(bitmap.getPixel(0, y));
            for (int x = 1; x < w; x++) {
                int gray = toGray(bitmap.getPixel(x, y));
                bits[idx++] = gray > prevGray;
                prevGray = gray;
            }
        }
        return bits;
    }

    private static int toGray(int pixel) {
        return (int) (0.299 * ((pixel >> 16) & 0xFF) +
                0.587 * ((pixel >> 8) & 0xFF) +
                0.114 * (pixel & 0xFF));
    }

    private static double bitSimilarity(boolean[] a, boolean[] b) {
        int matches = 0;
        for (int i = 0; i < a.length; i++) if (a[i] == b[i]) matches++;
        return matches / (double) a.length;
    }

    // Get similarity percentage string
    public static String getSimilarityText(double score) {
        if (score >= 0.8) return "🔥 Very High Match (" + Math.round(score * 100) + "%)";
        if (score >= 0.6) return "✅ High Match (" + Math.round(score * 100) + "%)";
        if (score >= 0.4) return "⚡ Moderate Match (" + Math.round(score * 100) + "%)";
        if (score >= 0.2) return "🔍 Low Match (" + Math.round(score * 100) + "%)";
        return "❌ No Match";
    }
}
