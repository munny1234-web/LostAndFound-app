package com.example.lostandfound;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;
public class MyPostsActivity extends AppCompatActivity {
    ListView listMyPosts;
    Button btnAllPosts, btnLostPosts, btnFoundPosts;
    TextView btnBack;
    DatabaseReference dbRef;
    String uid, currentFilter="all";
    ArrayList<Item> allMyItems=new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_posts);
        uid=FirebaseAuth.getInstance().getCurrentUser().getUid();
        dbRef=FirebaseDatabase.getInstance().getReference();
        listMyPosts=findViewById(R.id.listMyPosts);
        btnAllPosts=findViewById(R.id.btnAllPosts); btnLostPosts=findViewById(R.id.btnLostPosts);
        btnFoundPosts=findViewById(R.id.btnFoundPosts); btnBack=findViewById(R.id.btnBack);
        btnAllPosts.setOnClickListener(v->{currentFilter="all";show();});
        btnLostPosts.setOnClickListener(v->{currentFilter="lost";show();});
        btnFoundPosts.setOnClickListener(v->{currentFilter="found";show();});
        btnBack.setOnClickListener(v->finish());
        dbRef.child("items").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                allMyItems.clear();
                for(DataSnapshot s:snap.getChildren()) {
                    if(uid.equals(s.child("userId").getValue(String.class))) {
                        Item item=new Item(); item.id=s.getKey();
                        item.title=s.child("title").getValue(String.class);
                        item.description=s.child("description").getValue(String.class);
                        item.category=s.child("category").getValue(String.class);
                        item.location=s.child("location").getValue(String.class);
                        item.reward=s.child("reward").getValue(String.class);
                        item.date=s.child("date").getValue(String.class);
                        item.type=s.child("type").getValue(String.class);
                        item.imageUrl=s.child("imageUrl").getValue(String.class);
                        item.ocrText=s.child("ocrText").getValue(String.class);
                        item.latitude = s.child("latitude").getValue(Double.class);
                        item.longitude = s.child("longitude").getValue(Double.class);
                        item.labelsText=s.child("labelsText").getValue(String.class);
                        item.images = Item.readImagesMap(s, "images");
                        allMyItems.add(0,item);
                    }
                }
                show();
            }
            @Override public void onCancelled(@NonNull DatabaseError e){}
        });
    }
    void show() {
        ArrayList<Item> f=new ArrayList<>();
        for(Item i:allMyItems) if(currentFilter.equals("all")||currentFilter.equals(i.type)) f.add(i);
        ArrayAdapter<Item> adapter=new ArrayAdapter<Item>(this,R.layout.item_post,f) {
            @NonNull @Override
            public View getView(int pos,@Nullable View cv,@NonNull ViewGroup parent) {
                if(cv==null) cv=getLayoutInflater().inflate(R.layout.item_post,parent,false);
                Item item=f.get(pos); boolean isLost="lost".equals(item.type);
                TextView tvType=cv.findViewById(R.id.tvType);
                tvType.setText(isLost?"LOST":"FOUND");
                tvType.setTextColor(isLost?0xFFFF4444:0xFF2ECC71);
                tvType.setBackgroundResource(isLost?R.drawable.tag_lost:R.drawable.tag_found);
                ((TextView)cv.findViewById(R.id.tvCategory)).setText(item.category!=null?item.category:"");
                ((TextView)cv.findViewById(R.id.tvTitle)).setText(item.title);
                ((TextView)cv.findViewById(R.id.tvLocation)).setText(item.location!=null?item.location:"");
                ((TextView)cv.findViewById(R.id.tvDate)).setText(item.date!=null?item.date:"");
                cv.findViewById(R.id.layoutPostedBy).setVisibility(View.GONE);
                ImageView img=cv.findViewById(R.id.imgItem);
                ImageUtils.loadItemImage(MyPostsActivity.this, item.imageUrl, img, R.drawable.chip_default);

                LinearLayout adminControls = cv.findViewById(R.id.layoutAdminControls);
                adminControls.setVisibility(View.VISIBLE);
                adminControls.findViewById(R.id.btnAdminEditThisPost).setOnClickListener(v -> showEditDialog(item));
                adminControls.findViewById(R.id.btnAdminDeleteThisPost).setOnClickListener(v ->
                        new AlertDialog.Builder(MyPostsActivity.this)
                                .setTitle("Delete Post")
                                .setMessage("Delete \"" + item.title + "\"? This cannot be undone.")
                                .setPositiveButton("Delete", (d, w) -> {
                                    dbRef.child("items").child(item.id).removeValue()
                                            .addOnCompleteListener(t -> Toast.makeText(MyPostsActivity.this,
                                                    "✅ Post deleted successfully!", Toast.LENGTH_SHORT).show());
                                })
                                .setNegativeButton("Cancel", null)
                                .show()
                );
                return cv;
            }
        };
        listMyPosts.setAdapter(adapter);
    }

    // Owner-only quick edit for their own post, with a confirmation step
    // before anything actually gets saved.
    void showEditDialog(Item item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_post, null);
        EditText editTitle = dialogView.findViewById(R.id.editTitle);
        EditText editDescription = dialogView.findViewById(R.id.editDescription);
        EditText editCategory = dialogView.findViewById(R.id.editCategory);
        EditText editLocation = dialogView.findViewById(R.id.editLocation);
        EditText editReward = dialogView.findViewById(R.id.editReward);

        editTitle.setText(item.title);
        editDescription.setText(item.description);
        editCategory.setText(item.category);
        editLocation.setText(item.location);
        editReward.setText(item.reward);

        new AlertDialog.Builder(this)
                .setTitle("Edit Post")
                .setView(dialogView)
                .setPositiveButton("Save", (d, w) -> {
                    String newTitle = editTitle.getText().toString().trim();
                    if (newTitle.isEmpty()) { Toast.makeText(this, "Title can't be empty", Toast.LENGTH_SHORT).show(); return; }

                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Changes")
                            .setMessage("Save these changes to \"" + newTitle + "\"?")
                            .setPositiveButton("Yes, Save", (d2, w2) -> {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("title", newTitle);
                                updates.put("description", editDescription.getText().toString().trim());
                                updates.put("category", editCategory.getText().toString().trim());
                                updates.put("location", editLocation.getText().toString().trim());
                                updates.put("reward", editReward.getText().toString().trim());
                                dbRef.child("items").child(item.id).updateChildren(updates)
                                        .addOnCompleteListener(t -> Toast.makeText(MyPostsActivity.this,
                                                t.isSuccessful() ? "✅ Post updated successfully!" : "Update failed", Toast.LENGTH_SHORT).show());
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
