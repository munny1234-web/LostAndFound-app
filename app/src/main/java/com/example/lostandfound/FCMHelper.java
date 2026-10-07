package com.example.lostandfound;

import com.google.firebase.database.*;
import java.util.*;

public class FCMHelper {

    // Save notification to Firebase for a user. type/itemId/senderId/senderName/itemTitle
    // let NotificationsActivity know where to navigate when the user taps it.
    public static void sendNotification(String toUserId, String title, String message, String icon,
                                        String type, String itemId, String itemTitle,
                                        String senderId, String senderName) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("message", message);
        notification.put("icon", icon != null ? icon : "🔔");
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);
        notification.put("type", type != null ? type : "");
        notification.put("itemId", itemId != null ? itemId : "");
        notification.put("itemTitle", itemTitle != null ? itemTitle : "");
        notification.put("senderId", senderId != null ? senderId : "");
        notification.put("senderName", senderName != null ? senderName : "");
        dbRef.child("notifications").child(toUserId).push().setValue(notification);
    }

    // Notify when a match is found
    public static void notifyMatchFound(String toUserId, String itemId, String itemTitle) {
        sendNotification(toUserId,
                "🤖 Match Found!",
                "A possible match was found for your item: " + itemTitle,
                "🔍", "match", itemId, itemTitle, null, null);
    }

    // Notify when someone sends a message
    public static void notifyNewMessage(String toUserId, String fromUserId, String fromName, String itemId, String itemTitle) {
        sendNotification(toUserId,
                "💬 New Message",
                fromName + " sent you a message about: " + itemTitle,
                "💬", "message", itemId, itemTitle, fromUserId, fromName);
    }

    // Notify when item status changes
    public static void notifyStatusChange(String toUserId, String itemId, String itemTitle, String newStatus) {
        sendNotification(toUserId,
                "📋 Status Updated",
                itemTitle + " status: " + newStatus,
                "📋", "status", itemId, itemTitle, null, null);
    }

    // Notify when someone claims your item
    public static void notifyNewClaim(String toUserId, String itemId, String itemTitle, String claimantName) {
        sendNotification(toUserId,
                "📋 New Claim Request",
                claimantName + " claimed your item: " + itemTitle,
                "📋", "claim", itemId, itemTitle, null, claimantName);
    }

    // Notify the claimant once the owner approves or rejects their claim
    public static void notifyClaimResult(String toUserId, String itemId, String itemTitle, boolean approved) {
        sendNotification(toUserId,
                approved ? "🎉 Claim Approved!" : "Claim Update",
                approved
                        ? "Your claim for \"" + itemTitle + "\" was approved! Coordinate a safe handover with the finder."
                        : "Your claim for \"" + itemTitle + "\" wasn't approved this time.",
                approved ? "🎉" : "📋", "claim", itemId, itemTitle, null, null);
    }

    // Notify when someone reports your post
    public static void notifyReportSubmitted(String adminUserId, String itemId, String itemTitle, String reason) {
        sendNotification(adminUserId,
                "⚠️ New Report",
                "Post \"" + itemTitle + "\" was reported: " + reason,
                "⚠️", "report", itemId, itemTitle, null, null);
    }
}
