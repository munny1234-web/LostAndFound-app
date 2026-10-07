package com.example.lostandfound;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

// Firebase Cloud Messaging (real push, works even when the app is fully
// closed) needs a paid Cloud Functions backend to send messages between
// devices. Since this project stays on the free plan, this class instead
// shows a real Android system notification the moment a new entry appears
// under "notifications/{uid}" WHILE THE APP PROCESS IS ALIVE (foreground or
// backgrounded) — no separate server needed.
public class LostFoundApp extends Application {

    private static final String CHANNEL_ID = "lost_found_alerts";
    private String listeningForUid = null;
    private long listenerAttachedAt = 0;
    private Query notifQuery;
    private ChildEventListener notifListener;

    @Override
    public void onCreate() {
        super.onCreate();

        // MUST be the very first Firebase Database call in the whole app —
        // once anything else touches the database (even indirectly, like the
        // AuthStateListener below firing for an already-logged-in user), this
        // throws "setPersistenceEnabled() must be called before any other
        // usage" and crashes on every launch. try/catch is extra safety in
        // case this ever runs twice in the same process.
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignored) {}

        createNotificationChannel();

        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
            if (uid == null) {
                detachListener();
                listeningForUid = null;
                return;
            }
            if (uid.equals(listeningForUid)) return; // already listening for this user
            detachListener();
            listeningForUid = uid;
            attachListener(uid);
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Lost & Found Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts for new messages, matches, and claims");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void attachListener(String uid) {
        listenerAttachedAt = System.currentTimeMillis();
        notifQuery = FirebaseDatabase.getInstance().getReference()
                .child("notifications").child(uid);
        notifListener = new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot snap, String prevKey) {
                Long ts = snap.child("timestamp").getValue(Long.class);
                // Skip existing notifications from before this listener started
                // (onChildAdded fires once for every existing child on attach too).
                if (ts == null || ts < listenerAttachedAt) return;

                String title = snap.child("title").getValue(String.class);
                String message = snap.child("message").getValue(String.class);
                showSystemNotification(
                        title != null ? title : "🔔 Notification",
                        message != null ? message : "");
            }
            @Override public void onChildChanged(DataSnapshot snap, String prevKey) {}
            @Override public void onChildRemoved(DataSnapshot snap) {}
            @Override public void onChildMoved(DataSnapshot snap, String prevKey) {}
            @Override public void onCancelled(DatabaseError error) {}
        };
        notifQuery.addChildEventListener(notifListener);
    }

    private void detachListener() {
        if (notifQuery != null && notifListener != null) {
            notifQuery.removeEventListener(notifListener);
        }
        notifQuery = null;
        notifListener = null;
    }

    private void showSystemNotification(String title, String message) {
        Intent intent = new Intent(this, NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
