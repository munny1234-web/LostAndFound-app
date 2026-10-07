package com.example.lostandfound;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Professional analytics board.
 * Every number is computed for the selected period and compared against the
 * previous period of equal length (the "vs prev" deltas), the way large
 * product dashboards (Stripe / Amplitude style) present statistics.
 */
public class AdminAnalyticsActivity extends AppCompatActivity {

    // palette
    static final int TXT = 0xFF14142B, MUT = 0xFF7A7896, BD = 0xFFF1F0F8;
    static final int RED = 0xFFF04444, GREEN = 0xFF0E9F6E, INDIGO = 0xFF6C63FF,
            DEEP = 0xFF4834D4, AMBER = 0xFFF59E0B, TRACK = 0xFFEEEDF6;

    TextView btnBack, tvPeriodNote, tvHeroNote, tvCatNote, tvFunnelNote;
    TextView tvKpiPosts, tvKpiPostsD, tvKpiClaims, tvKpiClaimsD, tvKpiLost, tvKpiLostD,
            tvKpiFound, tvKpiFoundD, tvKpiRes, tvKpiResD, tvKpiMembers, tvKpiMembersD;
    Button btnR7, btnR30, btnR90, btnR12, btnRAll;
    LineChart lineChart;
    BarChart dowChart;
    LinearLayout layoutCatTable, layoutFunnel, layoutQuality, layoutLocTable, layoutMemTable;
    DatabaseReference dbRef;

    static class Row { String type, category, location, userName, userId; long ts; boolean resolved; }
    static class ClaimRow { String status; double ai; long ts; }
    final List<Row> items = new ArrayList<>();
    final List<ClaimRow> claims = new ArrayList<>();

    int rangeDays = 30; // 7 / 30 / 90 / 365 / 0 = all

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AdminUtils.isAdmin(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser())) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_admin_analytics);

        dbRef = FirebaseDatabase.getInstance().getReference();
        btnBack = findViewById(R.id.btnBack);
        tvPeriodNote = findViewById(R.id.tvPeriodNote);
        tvHeroNote = findViewById(R.id.tvHeroNote);
        tvCatNote = findViewById(R.id.tvCatNote);
        tvFunnelNote = findViewById(R.id.tvFunnelNote);
        tvKpiPosts = findViewById(R.id.tvKpiPosts);    tvKpiPostsD = findViewById(R.id.tvKpiPostsD);
        tvKpiClaims = findViewById(R.id.tvKpiClaims);  tvKpiClaimsD = findViewById(R.id.tvKpiClaimsD);
        tvKpiLost = findViewById(R.id.tvKpiLost);      tvKpiLostD = findViewById(R.id.tvKpiLostD);
        tvKpiFound = findViewById(R.id.tvKpiFound);    tvKpiFoundD = findViewById(R.id.tvKpiFoundD);
        tvKpiRes = findViewById(R.id.tvKpiRes);        tvKpiResD = findViewById(R.id.tvKpiResD);
        tvKpiMembers = findViewById(R.id.tvKpiMembers);tvKpiMembersD = findViewById(R.id.tvKpiMembersD);
        btnR7 = findViewById(R.id.btnR7);   btnR30 = findViewById(R.id.btnR30);
        btnR90 = findViewById(R.id.btnR90); btnR12 = findViewById(R.id.btnR12);
        btnRAll = findViewById(R.id.btnRAll);
        lineChart = findViewById(R.id.lineChart);
        dowChart = findViewById(R.id.dowChart);
        layoutCatTable = findViewById(R.id.layoutCatTable);
        layoutFunnel = findViewById(R.id.layoutFunnel);
        layoutQuality = findViewById(R.id.layoutQuality);
        layoutLocTable = findViewById(R.id.layoutLocTable);
        layoutMemTable = findViewById(R.id.layoutMemTable);

        btnBack.setOnClickListener(v -> finish());
        btnR7.setOnClickListener(v -> setRange(7));
        btnR30.setOnClickListener(v -> setRange(30));
        btnR90.setOnClickListener(v -> setRange(90));
        btnR12.setOnClickListener(v -> setRange(365));
        btnRAll.setOnClickListener(v -> setRange(0));

        loadData();
    }

    void setRange(int d) {
        rangeDays = d;
        Button[] all = { btnR7, btnR30, btnR90, btnR12, btnRAll };
        int[] vals = { 7, 30, 90, 365, 0 };
        for (int i = 0; i < all.length; i++) {
            boolean sel = vals[i] == d;
            if (sel) all[i].setBackgroundResource(R.drawable.seg_thumb);
            else all[i].setBackgroundColor(Color.TRANSPARENT);
            all[i].setTextColor(sel ? TXT : MUT);
        }
        buildAll();
    }

    void loadData() {
        dbRef.child("items").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                items.clear();
                for (DataSnapshot s : snap.getChildren()) {
                    Row r = new Row();
                    r.type = s.child("type").getValue(String.class);
                    r.category = s.child("category").getValue(String.class);
                    r.location = s.child("location").getValue(String.class);
                    r.userName = s.child("userName").getValue(String.class);
                    r.userId = s.child("userId").getValue(String.class);
                    Long ts = s.child("timestamp").getValue(Long.class);
                    r.ts = ts != null ? ts : 0;
                    String st = s.child("status").getValue(String.class);
                    r.resolved = st != null && (st.contains("Found") || st.contains("Returned"));
                    items.add(r);
                }
                buildAll();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
        dbRef.child("claims").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                claims.clear();
                for (DataSnapshot s : snap.getChildren()) {
                    ClaimRow c = new ClaimRow();
                    c.status = s.child("status").getValue(String.class);
                    Long ts = s.child("timestamp").getValue(Long.class);
                    c.ts = ts != null ? ts : 0;
                    Long pct = s.child("aiMatchPercentage").getValue(Long.class);
                    if (pct != null) c.ai = pct;
                    else {
                        Double hidden = s.child("hiddenMatchScore").getValue(Double.class);
                        c.ai = hidden != null ? hidden * 100.0 : 0;
                    }
                    claims.add(c);
                }
                buildAll();
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }

    /* ==================== period windows ==================== */
    long DAY = 86400000L;
    long curStart(){ return rangeDays == 0 ? 0 : System.currentTimeMillis() - rangeDays * DAY; }
    boolean inCur(long ts){ return ts >= curStart(); }
    boolean inPrev(long ts){
        if (rangeDays == 0) return false;
        long now = System.currentTimeMillis();
        return ts >= now - 2L * rangeDays * DAY && ts < now - rangeDays * DAY;
    }
    String periodLabel(){
        if (rangeDays == 0) return "All time";
        if (rangeDays == 365) return "Last 12 months";
        return "Last " + rangeDays + " days";
    }

    void setDelta(TextView tv, int cur, int prev) {
        if (rangeDays == 0) { tv.setText("all time"); tv.setTextColor(MUT); return; }
        if (prev == 0) {
            if (cur > 0) { tv.setText("▲ new"); tv.setTextColor(GREEN); }
            else { tv.setText("— 0%"); tv.setTextColor(MUT); }
            return;
        }
        int d = Math.round((cur - prev) * 100f / prev);
        if (d > 0) { tv.setText("▲ " + d + "% vs prev"); tv.setTextColor(GREEN); }
        else if (d < 0) { tv.setText("▼ " + Math.abs(d) + "% vs prev"); tv.setTextColor(RED); }
        else { tv.setText("— 0% vs prev"); tv.setTextColor(MUT); }
    }

    /* ==================== build everything ==================== */
    void buildAll() {
        List<Row> cur = new ArrayList<>(), prev = new ArrayList<>();
        for (Row r : items) { if (inCur(r.ts)) cur.add(r); else if (inPrev(r.ts)) prev.add(r); }
        List<ClaimRow> curC = new ArrayList<>(), prevC = new ArrayList<>();
        for (ClaimRow c : claims) { if (inCur(c.ts)) curC.add(c); else if (inPrev(c.ts)) prevC.add(c); }

        tvPeriodNote.setText(rangeDays == 0 ? "All time"
                : periodLabel() + " · compared with previous period");
        tvHeroNote.setText(periodLabel());
        tvCatNote.setText(periodLabel());
        tvFunnelNote.setText(periodLabel());

        /* ---- KPIs ---- */
        int lost = 0, found = 0, resolved = 0, pLost = 0, pFound = 0, pResolved = 0;
        Set<String> members = new HashSet<>(), pMembers = new HashSet<>();
        for (Row r : cur) {
            if ("lost".equals(r.type)) lost++; else found++;
            if (r.resolved) resolved++;
            if (r.userId != null) members.add(r.userId);
        }
        for (Row r : prev) {
            if ("lost".equals(r.type)) pLost++; else pFound++;
            if (r.resolved) pResolved++;
            if (r.userId != null) pMembers.add(r.userId);
        }
        tvKpiPosts.setText(String.valueOf(cur.size()));   setDelta(tvKpiPostsD, cur.size(), prev.size());
        tvKpiLost.setText(String.valueOf(lost));          setDelta(tvKpiLostD, lost, pLost);
        tvKpiFound.setText(String.valueOf(found));        setDelta(tvKpiFoundD, found, pFound);
        tvKpiClaims.setText(String.valueOf(curC.size())); setDelta(tvKpiClaimsD, curC.size(), prevC.size());
        int rr = cur.isEmpty() ? 0 : Math.round(resolved * 100f / cur.size());
        int prr = prev.isEmpty() ? 0 : Math.round(pResolved * 100f / prev.size());
        tvKpiRes.setText(rr + "%");                       setDelta(tvKpiResD, rr, prr);
        tvKpiMembers.setText(String.valueOf(members.size())); setDelta(tvKpiMembersD, members.size(), pMembers.size());

        buildHero(cur);
        buildCategoryTable(cur);
        buildFunnel(curC);
        buildWeekday(cur);
        buildQuality(curC);
        buildLocations(cur);
        buildMembers(cur);
    }

    /* ---- hero line: clean, no dots, thin y-grid ---- */
    void buildHero(List<Row> cur) {
        boolean monthly = rangeDays == 365 || rangeDays == 0;
        List<String> keys = new ArrayList<>(), labels = new ArrayList<>();
        Calendar now = Calendar.getInstance();

        if (monthly) {
            Calendar start = (Calendar) now.clone();
            if (rangeDays == 0 && !items.isEmpty()) {
                long first = Long.MAX_VALUE;
                for (Row r : items) if (r.ts > 0 && r.ts < first) first = r.ts;
                if (first == Long.MAX_VALUE) first = System.currentTimeMillis();
                start.setTimeInMillis(first);
            } else start.add(Calendar.MONTH, -11);
            start.set(Calendar.DAY_OF_MONTH, 1);
            SimpleDateFormat lf = new SimpleDateFormat("MMM", Locale.US);
            Calendar c = (Calendar) start.clone();
            while (!c.after(now)) {
                keys.add(c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH));
                labels.add(lf.format(c.getTime()));
                c.add(Calendar.MONTH, 1);
            }
        } else {
            SimpleDateFormat lf = new SimpleDateFormat("d MMM", Locale.US);
            for (int i = rangeDays - 1; i >= 0; i--) {
                Calendar c = (Calendar) now.clone();
                c.add(Calendar.DAY_OF_YEAR, -i);
                keys.add(c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR));
                labels.add(lf.format(c.getTime()));
            }
        }

        float[] lostS = new float[keys.size()], foundS = new float[keys.size()];
        Calendar c = Calendar.getInstance();
        List<Row> src = rangeDays == 0 ? items : cur;
        for (Row r : src) {
            if (r.ts <= 0) continue;
            c.setTimeInMillis(r.ts);
            String k = monthly ? c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH)
                    : c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
            int idx = keys.indexOf(k);
            if (idx >= 0) { if ("lost".equals(r.type)) lostS[idx]++; else foundS[idx]++; }
        }

        List<Entry> le = new ArrayList<>(), fe = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) { le.add(new Entry(i, lostS[i])); fe.add(new Entry(i, foundS[i])); }

        LineData data = new LineData(cleanLine(le, RED), cleanLine(fe, GREEN));
        lineChart.setData(data);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(true);
        XAxis x = lineChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setTextSize(9f);
        x.setTextColor(MUT);
        x.setDrawGridLines(false);
        x.setLabelCount(Math.min(labels.size(), 7));
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setGranularity(1f);
        lineChart.getAxisLeft().setTextColor(MUT);
        lineChart.getAxisLeft().setGridColor(BD);
        lineChart.getAxisLeft().setDrawAxisLine(false);
        lineChart.animateX(500);
        lineChart.invalidate();
    }

    LineDataSet cleanLine(List<Entry> e, int color) {
        LineDataSet ds = new LineDataSet(e, "");
        ds.setColor(color);
        ds.setLineWidth(2f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setDrawFilled(true);
        ds.setFillColor(color);
        ds.setFillAlpha(14);
        return ds;
    }

    /* ---- category table: name | lost | found | total | share bar ---- */
    void buildCategoryTable(List<Row> cur) {
        layoutCatTable.removeAllViews();
        Map<String, int[]> split = new HashMap<>();
        for (Row r : cur) {
            String cat = r.category != null && !r.category.isEmpty() ? r.category : "Other";
            int[] v = split.get(cat);
            if (v == null) { v = new int[2]; split.put(cat, v); }
            if ("lost".equals(r.type)) v[0]++; else v[1]++;
        }
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(split.entrySet());
        sorted.sort((a, b) -> (b.getValue()[0] + b.getValue()[1]) - (a.getValue()[0] + a.getValue()[1]));
        if (sorted.isEmpty()) { layoutCatTable.addView(emptyText()); return; }

        layoutCatTable.addView(headerRow("Category", "Lost", "Found", "Share"));
        int total = cur.size();
        for (Map.Entry<String, int[]> e : sorted) {
            int l = e.getValue()[0], f = e.getValue()[1], t = l + f;
            layoutCatTable.addView(dataRow(e.getKey(),
                    l > 0 ? String.valueOf(l) : "·", RED,
                    f > 0 ? String.valueOf(f) : "·", GREEN,
                    total > 0 ? t * 100 / total : 0, l, f));
        }
    }

    /* ---- claims funnel ---- */
    void buildFunnel(List<ClaimRow> curC) {
        layoutFunnel.removeAllViews();
        int total = curC.size(), a = 0, rj = 0, p = 0;
        for (ClaimRow c : curC) {
            String s = c.status != null ? c.status : "";
            if (s.contains("Approved")) a++; else if (s.contains("Rejected")) rj++; else p++;
        }
        if (total == 0) { layoutFunnel.addView(emptyText()); return; }
        layoutFunnel.addView(funnelRow("Submitted", total, total, INDIGO));
        layoutFunnel.addView(funnelRow("Approved", a, total, GREEN));
        layoutFunnel.addView(funnelRow("Pending", p, total, AMBER));
        layoutFunnel.addView(funnelRow("Rejected", rj, total, RED));

        TextView sum = new TextView(this);
        sum.setText("Approval rate " + Math.round(a * 100f / total) + "% of all claims in period");
        sum.setTextColor(MUT);
        sum.setTextSize(12f);
        sum.setPadding(0, dp(6), 0, 0);
        layoutFunnel.addView(sum);
    }

    /* ---- weekday: quiet bars, busiest highlighted ---- */
    void buildWeekday(List<Row> cur) {
        float[] counts = new float[7];
        Calendar c = Calendar.getInstance();
        for (Row r : cur) {
            if (r.ts <= 0) continue;
            c.setTimeInMillis(r.ts);
            counts[c.get(Calendar.DAY_OF_WEEK) - 1]++;
        }
        float max = 0;
        for (float v : counts) if (v > max) max = v;
        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.add(new BarEntry(i, counts[i]));
            colors.add(counts[i] == max && max > 0 ? DEEP : 0xFFDDDBF3);
        }
        BarDataSet ds = new BarDataSet(entries, "");
        ds.setColors(colors);
        ds.setValueTextColor(MUT);
        ds.setValueTextSize(10f);
        ds.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return value > 0 ? String.valueOf((int) value) : "";
            }
        });
        BarData data = new BarData(ds);
        data.setBarWidth(0.55f);
        dowChart.setData(data);
        dowChart.getDescription().setEnabled(false);
        dowChart.getLegend().setEnabled(false);
        dowChart.setTouchEnabled(false);
        XAxis x = dowChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"Sun","Mon","Tue","Wed","Thu","Fri","Sat"}));
        x.setTextColor(MUT);
        x.setDrawGridLines(false);
        dowChart.getAxisLeft().setEnabled(false);
        dowChart.getAxisRight().setEnabled(false);
        dowChart.animateY(500);
        dowChart.invalidate();
    }

    /* ---- verification quality: one segmented 100% band ---- */
    void buildQuality(List<ClaimRow> curC) {
        layoutQuality.removeAllViews();
        int weak = 0, partial = 0, strong = 0;
        for (ClaimRow c : curC) {
            if (c.ai >= 75) strong++; else if (c.ai >= 45) partial++; else weak++;
        }
        int total = weak + partial + strong;
        if (total == 0) { layoutQuality.addView(emptyText()); return; }

        LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24));
        band.setLayoutParams(blp);
        addSeg(band, weak, total, RED);
        addSeg(band, partial, total, AMBER);
        addSeg(band, strong, total, GREEN);
        layoutQuality.addView(band);

        layoutQuality.addView(legendRow(RED, "Weak (<45%)  ·  " + weak));
        layoutQuality.addView(legendRow(AMBER, "Partial (45–74%)  ·  " + partial));
        layoutQuality.addView(legendRow(GREEN, "Strong (75%+)  ·  " + strong));

        TextView sum = new TextView(this);
        sum.setText(Math.round(strong * 100f / total) + "% of claims scored a strong AI match — higher is healthier.");
        sum.setTextColor(MUT);
        sum.setTextSize(12f);
        sum.setPadding(0, dp(8), 0, 0);
        layoutQuality.addView(sum);
    }

    void addSeg(LinearLayout band, int n, int total, int color) {
        if (n <= 0) return;
        TextView seg = new TextView(this);
        seg.setText(String.valueOf(n));
        seg.setTextColor(Color.WHITE);
        seg.setTextSize(11f);
        seg.setTypeface(null, Typeface.BOLD);
        seg.setGravity(Gravity.CENTER);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        seg.setBackground(gd);
        band.addView(seg, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, n));
    }

    TextView legendRow(int color, String text) {
        TextView tv = new TextView(this);
        tv.setText("●  " + text);
        tv.setTextColor(MUT);
        tv.setTextSize(12f);
        tv.setPadding(0, dp(6), 0, 0);
        android.text.SpannableString sp = new android.text.SpannableString(tv.getText());
        sp.setSpan(new android.text.style.ForegroundColorSpan(color), 0, 1, 0);
        tv.setText(sp);
        return tv;
    }

    /* ---- location + member tables ---- */
    void buildLocations(List<Row> cur) {
        layoutLocTable.removeAllViews();
        Map<String, int[]> locs = new HashMap<>();
        for (Row r : cur) {
            if (r.location == null || r.location.trim().isEmpty()) continue;
            String k = r.location.split(",")[0].trim();
            if (k.isEmpty()) continue;
            int[] v = locs.get(k);
            if (v == null) { v = new int[2]; locs.put(k, v); }
            if ("lost".equals(r.type)) v[0]++; else v[1]++;
        }
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(locs.entrySet());
        sorted.sort((a, b) -> (b.getValue()[0] + b.getValue()[1]) - (a.getValue()[0] + a.getValue()[1]));
        if (sorted.isEmpty()) { layoutLocTable.addView(emptyText()); return; }
        layoutLocTable.addView(headerRow("Location", "Lost", "Found", "Total"));
        int limit = Math.min(7, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, int[]> e = sorted.get(i);
            layoutLocTable.addView(simpleRow(e.getKey(),
                    e.getValue()[0] > 0 ? String.valueOf(e.getValue()[0]) : "·", RED,
                    e.getValue()[1] > 0 ? String.valueOf(e.getValue()[1]) : "·", GREEN,
                    String.valueOf(e.getValue()[0] + e.getValue()[1])));
        }
    }

    void buildMembers(List<Row> cur) {
        layoutMemTable.removeAllViews();
        Map<String, Integer> posters = new HashMap<>();
        for (Row r : cur) posters.merge(r.userName != null ? r.userName : "Unknown", 1, Integer::sum);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(posters.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        if (sorted.isEmpty()) { layoutMemTable.addView(emptyText()); return; }
        layoutMemTable.addView(headerRow("Member", "", "", "Posts"));
        int limit = Math.min(7, sorted.size());
        for (int i = 0; i < limit; i++) {
            layoutMemTable.addView(simpleRow(sorted.get(i).getKey(), "", MUT, "", MUT,
                    String.valueOf(sorted.get(i).getValue())));
        }
    }

    /* ==================== small view builders ==================== */
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    TextView emptyText() {
        TextView tv = new TextView(this);
        tv.setText("No data in this period.");
        tv.setTextColor(MUT);
        tv.setTextSize(13f);
        tv.setPadding(0, dp(10), 0, dp(4));
        return tv;
    }

    LinearLayout headerRow(String a, String b, String c, String d) {
        LinearLayout row = baseRow();
        row.addView(cell(a, MUT, 1f, false, true, Gravity.START));
        row.addView(cell(b, MUT, 0f, false, true, Gravity.END));
        row.addView(cell(c, MUT, 0f, false, true, Gravity.END));
        row.addView(cell(d, MUT, 0f, false, true, Gravity.END));
        return row;
    }

    LinearLayout simpleRow(String name, String l, int lc, String f, int fc, String total) {
        LinearLayout row = baseRow();
        row.addView(cell(name, TXT, 1f, false, false, Gravity.START));
        row.addView(cell(l, lc, 0f, false, false, Gravity.END));
        row.addView(cell(f, fc, 0f, false, false, Gravity.END));
        row.addView(cell(total, TXT, 0f, true, false, Gravity.END));
        return row;
    }

    LinearLayout dataRow(String name, String l, int lc, String f, int fc, int sharePct, int lostN, int foundN) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(8), 0, dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cell(name, TXT, 1f, false, false, Gravity.START));
        row.addView(cell(l, lc, 0f, false, false, Gravity.END));
        row.addView(cell(f, fc, 0f, false, false, Gravity.END));
        row.addView(cell(sharePct + "%", MUT, 0f, true, false, Gravity.END));
        wrap.addView(row);

        // two-color share bar (lost red + found green out of period total)
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(TRACK);
        tbg.setCornerRadius(dp(3));
        track.setBackground(tbg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        tlp.topMargin = dp(6);
        track.setLayoutParams(tlp);
        int t = lostN + foundN;
        if (t > 0) {
            float scale = sharePct / 100f;
            addFill(track, lostN * scale / t, RED);
            addFill(track, foundN * scale / t, GREEN);
            addFill(track, 1f - scale, Color.TRANSPARENT);
        }
        wrap.addView(track);
        return wrap;
    }

    void addFill(LinearLayout track, float weight, int color) {
        if (weight <= 0) return;
        android.view.View v = new android.view.View(this);
        if (color != Color.TRANSPARENT) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(color);
            gd.setCornerRadius(dp(3));
            v.setBackground(gd);
        }
        track.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight));
    }

    LinearLayout funnelRow(String label, int n, int total, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(TXT);
        lbl.setTextSize(13f);
        lbl.setTypeface(null, Typeface.BOLD);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(lbl);

        LinearLayout track = new LinearLayout(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(BD);
        tbg.setCornerRadius(dp(6));
        track.setBackground(tbg);
        track.setLayoutParams(new LinearLayout.LayoutParams(0, dp(24), 1f));
        float w = total > 0 ? (float) n / total : 0;
        if (n > 0) {
            TextView fill = new TextView(this);
            fill.setText(String.valueOf(n));
            fill.setTextColor(Color.WHITE);
            fill.setTextSize(11f);
            fill.setTypeface(null, Typeface.BOLD);
            fill.setGravity(Gravity.CENTER_VERTICAL);
            fill.setPadding(dp(8), 0, 0, 0);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(color);
            gd.setCornerRadius(dp(6));
            fill.setBackground(gd);
            track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(w, 0.12f)));
            android.view.View sp = new android.view.View(this);
            track.addView(sp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - Math.max(w, 0.12f)));
        }
        row.addView(track);

        TextView pct = new TextView(this);
        pct.setText(total > 0 ? Math.round(n * 100f / total) + "%" : "0%");
        pct.setTextColor(MUT);
        pct.setTextSize(12f);
        pct.setGravity(Gravity.END);
        pct.setLayoutParams(new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(pct);
        return row;
    }

    LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        return row;
    }

    TextView cell(String text, int color, float weight, boolean bold, boolean caps, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(caps ? 10f : 13f);
        tv.setAllCaps(caps);
        if (caps) tv.setLetterSpacing(0.06f);
        if (bold || caps) tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(gravity);
        LinearLayout.LayoutParams lp = weight > 0
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                : new LinearLayout.LayoutParams(dp(56), LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }
}
