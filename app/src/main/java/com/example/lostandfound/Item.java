package com.example.lostandfound;

import com.google.firebase.database.DataSnapshot;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Item implements Serializable {
    public String id, title, description, category, location, date, contact, reward, imageUrl, type, userId, userName;
    public String ocrText; // text detected on the item's photo (ID numbers, brand names, labels) via ML Kit OCR
    public String labelsText; // raw ML Kit image-labels with confidence, e.g. "Wristwatch (92%), Metal (81%)" — used for semantic photo matching
    public Double latitude, longitude; // optional map pin, set via LocationPickerActivity
    public String verificationQuestion; // secret detail only the finder knows — claimants must answer this to prove ownership
    public String verificationAnswer; // the CORRECT answer to that question (set by the finder, never shown to claimants)
    // Extra photos beyond the cover (imageUrl). Keys are "0","1","2"... — index 0
    // duplicates the cover photo (same convention the website uses), so any code
    // that only reads imageUrl keeps working untouched. Null/empty for old posts
    // and single-photo posts.
    public Map<String, String> images;
    public long timestamp;
    public double matchScore; // For ML matching

    public Item() {}

    // Reads a "images"/"proofImages"-style child node safely, no matter which
    // shape Firebase actually stored it as. Firebase Realtime Database
    // automatically stores an object with purely sequential keys ("0","1","2"...)
    // as a JSON ARRAY instead of a map — a well-known RTDB quirk. Reading such
    // a node with a strict Map<String,String> type indicator throws
    // "Expected a Map... but got ArrayList" and crashes. This reads the raw
    // value instead and normalizes either shape into a LinkedHashMap.
    public static Map<String, String> readImagesMap(DataSnapshot parentSnap, String childName) {
        DataSnapshot node = parentSnap.child(childName);
        if (!node.exists()) return null;
        Object raw = node.getValue();
        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            for (int i = 0; i < list.size(); i++) {
                Object v = list.get(i);
                if (v != null) result.put(String.valueOf(i), v.toString());
            }
        } else if (raw instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
                if (e.getValue() != null) result.put(String.valueOf(e.getKey()), e.getValue().toString());
            }
        }
        return result.isEmpty() ? null : result;
    }
}
