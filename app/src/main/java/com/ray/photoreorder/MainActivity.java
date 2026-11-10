package com.ray.photoreorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int PAGE = 200;

    private final List<MediaItem> items = new ArrayList<>();
    private GridAdapter adapter;
    private int limit = PAGE;
    private boolean running = false;
    private boolean loading = false;

    private TextView btnPick, btnClear, btnSelectAll, btnPanel, btnDirection;
    private Button btnAll, btnRun;
    private TextView txtStats, txtRangeHint1, txtRangeHint2, txtOrder, txtOrderHint,
            txtProgress, txtFoot, txtGridHint;
    private EditText inputStart, inputEnd;
    private RecyclerView grid;
    private ScrollView panel;
    private LinearLayout progressOverlay;

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPick = findViewById(R.id.btnPick);
        btnClear = findViewById(R.id.btnClear);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnPanel = findViewById(R.id.btnPanel);
        btnAll = findViewById(R.id.btnAll);
        btnRun = findViewById(R.id.btnRun);
        txtStats = findViewById(R.id.txtStats);
        txtGridHint = findViewById(R.id.txtGridHint);
        btnDirection = findViewById(R.id.btnDirection);
        txtRangeHint1 = findViewById(R.id.txtRangeHint1);
        txtRangeHint2 = findViewById(R.id.txtRangeHint2);
        txtOrder = findViewById(R.id.txtOrder);
        txtOrderHint = findViewById(R.id.txtOrderHint);
        txtProgress = findViewById(R.id.txtProgress);
        txtFoot = findViewById(R.id.txtFoot);
        inputStart = findViewById(R.id.inputStart);
        inputEnd = findViewById(R.id.inputEnd);
        grid = findViewById(R.id.grid);
        panel = findViewById(R.id.panel);
        progressOverlay = findViewById(R.id.progressOverlay);

        adapter = new GridAdapter(this, items, 4, this::onToggle);
        grid.setLayoutManager(new GridLayoutManager(this, 4));
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                // 快到底部时自动再读一批更早的素材
                if (lm.findLastVisibleItemPosition() >= items.size() - 12
                        && items.size() >= limit) {
                    loadMedia(limit + PAGE);
                }
            }
        });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (hasPermission()) loadMedia(limit);
                    else txtStats.setText("没有相册权限，无法读取素材");
                });

        btnPick.setOnClickListener(v -> {
            if (hasPermission()) loadMedia(PAGE);
            else permissionLauncher.launch(neededPermissions());
        });
        btnClear.setOnClickListener(v -> clearSelection());
        btnSelectAll.setOnClickListener(v -> selectAll());
        btnPanel.setOnClickListener(v -> togglePanel());
        btnDirection.setOnClickListener(v -> toggleDirection());
        btnAll.setOnClickListener(v -> {
            int n = selectedItems().size();
            boolean reverse = isReverseRange();
            inputStart.setText(reverse ? String.valueOf(n) : "1");
            inputEnd.setText(reverse ? "1" : String.valueOf(n));
            refreshAll();
        });
        btnRun.setOnClickListener(v -> confirmRun());

        watch(inputStart);
        watch(inputEnd);

        refreshAll();
        if (hasPermission()) loadMedia(PAGE);
        else permissionLauncher.launch(neededPermissions());
    }

    // ---------------------------------------------------------------- 权限

    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasPermission() {
        for (String p : neededPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 加载

    private void loadMedia(int newLimit) {
        if (loading) return;
        loading = true;
        limit = newLimit;
        txtStats.setText("正在读取相册…");
        new Thread(() -> {
            List<MediaItem> loaded = MediaRepo.load(this, limit);

            // 重新读取时保留用户已经做过的勾选
            final Map<Long, Boolean> prev = new HashMap<>();
            for (MediaItem m : items) prev.put(m.id, m.selected);

            runOnUiThread(() -> {
                loading = false;
                items.clear();
                for (MediaItem m : loaded) {
                    Boolean sel = prev.get(m.id);
                    if (sel != null) m.selected = sel;
                    items.add(m);
                }
                adapter.notifyDataSetChanged();
                if (inputEnd.getText().toString().trim().isEmpty()
                        || inputEnd.getText().toString().equals("1")) {
                    inputEnd.setText(String.valueOf(Math.max(1, selectedItems().size())));
                }
                refreshAll();
                if (loaded.isEmpty()) txtStats.setText("相册里没有读到素材");
            });
        }).start();
    }

    /** 全不选 */
    private void clearSelection() {
        for (MediaItem m : items) {
            m.selected = false;
            m.number = 0;
        }
        inputStart.setText("1");
        inputEnd.setText("1");
        adapter.notifyDataSetChanged();
        refreshAll();
    }

    private void selectAll() {
        for (MediaItem m : items) m.selected = true;
        adapter.notifyDataSetChanged();
        refreshAll();
    }

    private void togglePanel() {
        boolean show = panel.getVisibility() != View.VISIBLE;
        if (show) {
            // 面板最多占半屏，剩下的全留给网格，保证网格永远能滚
            panel.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            panel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            int max = (int) (getResources().getDisplayMetrics()
                                    .heightPixels * 0.5f);
                            if (panel.getHeight() > max) {
                                ViewGroup.LayoutParams lp = panel.getLayoutParams();
                                lp.height = max;
                                panel.setLayoutParams(lp);
                            }
                        }
                    });
        }
        panel.setVisibility(show ? View.VISIBLE : View.GONE);
        btnPanel.setText(show ? "参数 ▴" : "参数 ▾");
    }

    private void onToggle(int position) {
        if (position < 0 || position >= items.size()) return;
        MediaItem item = items.get(position);
        boolean wasFullCover = isFullCoverRange();
        item.selected = !item.selected;

        if (wasFullCover) {
            int n = selectedItems().size();
            if (isReverseRange()) {
                inputStart.setText(String.valueOf(Math.max(1, n)));
                inputEnd.setText("1");
            } else {
                inputStart.setText("1");
                inputEnd.setText(String.valueOf(Math.max(1, n)));
            }
        }
        adapter.notifyDataSetChanged();
        refreshAll();
    }

    // ---------------------------------------------------------------- 区间

    private List<MediaItem> selectedItems() {
        List<MediaItem> out = new ArrayList<>();
        for (MediaItem m : items) if (m.selected) out.add(m);
        return out;
    }

    private int clampInput(EditText field, int fallback) {
        int n = selectedItems().size();
        int value;
        try {
            value = Integer.parseInt(field.getText().toString().trim());
        } catch (Exception e) {
            value = fallback;
        }
        if (value < 1) value = 1;
        if (n > 0 && value > n) value = n;
        return value;
    }

    private int startValue() {
        return clampInput(inputStart, 1);
    }

    private int endValue() {
        int n = selectedItems().size();
        return clampInput(inputEnd, Math.max(1, n));
    }

    private boolean isReverseRange() {
        return startValue() > endValue();
    }

    private boolean isFullCoverRange() {
        int n = selectedItems().size();
        if (n == 0) return false;
        int s = startValue(), e = endValue();
        return (s == 1 && e == n) || (s == n && e == 1);
    }

    private void toggleDirection() {
        if (selectedItems().size() < 2) return;
        int s = startValue();
        int e = endValue();
        inputStart.setText(String.valueOf(e));
        inputEnd.setText(String.valueOf(s));
        refreshAll();
    }

    // ---------------------------------------------------------------- 刷新

    private void watch(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                refreshAll();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void refreshAll() {
        List<MediaItem> selected = selectedItems();
        int imageCount = 0, videoCount = 0;
        for (MediaItem m : selected) {
            if (m.video) videoCount++;
            else imageCount++;
        }

        if (items.isEmpty()) {
            txtStats.setText(loading ? "正在读取相册…" : "相册里没有读到素材");
        } else {
            txtStats.setText(String.format(Locale.CHINA, "共 %d 个 · 已选 %d · 图 %d · 视 %d",
                    items.size(), selected.size(), imageCount, videoCount));
        }
        btnSelectAll.setVisibility(selected.size() < items.size() && !items.isEmpty()
                ? View.VISIBLE : View.GONE);

        int n = selected.size();
        int s = startValue();
        int e = endValue();
        boolean reverse = s > e;

        btnDirection.setText(reverse ? "倒序 ↑" : "正序 ↓");
        btnDirection.setBackgroundResource(reverse
                ? R.drawable.bg_capsule_reverse : R.drawable.bg_capsule_normal);
        btnDirection.setTextColor(ContextCompat.getColor(this,
                reverse ? R.color.brand : R.color.text_secondary));
        btnDirection.setAlpha(n < 2 ? 0.4f : 1f);

        int amount = n == 0 ? 0 : Math.abs(s - e) + 1;
        txtRangeHint1.setText(n == 0 ? "先在网格里点选素材"
                : (reverse ? "倒序：从第 " + s + " 个往第 " + e + " 个走，共 " + amount + " 个"
                : "正序：从第 " + s + " 个往第 " + e + " 个走，共 " + amount + " 个"));
        txtRangeHint2.setText(n == 0 ? "" :
                "编号从「起」那格开始、朝「止」的方向每格 +1：编号 1 = 第 " + s
                        + " 个，编号 " + amount + " = 第 " + e + " 个");

        // 编号回填到网格
        for (MediaItem m : items) m.number = 0;
        Planner.Plan plan = n == 0 ? null : Planner.make(selected, s, e, System.currentTimeMillis());
        if (plan != null) {
            for (Planner.Entry entry : plan.entries) entry.item.number = entry.number;
            StringBuilder sb = new StringBuilder("相册顺序：分隔图");
            for (int i = 0; i < plan.entries.size(); i++) {
                Planner.Entry entry = plan.entries.get(i);
                sb.append(" → ").append(i + 1).append("·").append(entry.item.kindLabel());
            }
            txtOrder.setText(sb.toString());
            txtOrderHint.setText("这行是写进相册的先后（图视交替）；右上角红圈是烧进画面的编号，两者是两套号");
        } else {
            txtOrder.setText("—");
            txtOrderHint.setText("");
        }
        adapter.notifyDataSetChanged();

        boolean canRun = !running && amount > 0;
        btnRun.setEnabled(canRun);
        btnRun.setText(running ? "处理中…" : "重新排列" + (amount > 0 ? "（" + amount + " 个）" : ""));
        txtFoot.setText(videoCount > 0
                ? "视频要转码，会比较慢，别锁屏"
                : "图片处理较快，别锁屏");
    }

    // ---------------------------------------------------------------- 执行

    private void confirmRun() {
        List<MediaItem> selected = selectedItems();
        int n = selected.size();
        if (n == 0) {
            Toast.makeText(this, "先选素材", Toast.LENGTH_SHORT).show();
            return;
        }
        int s = startValue(), e = endValue();
        Planner.Plan plan = Planner.make(selected, s, e, System.currentTimeMillis());
        float trimSeconds = 0f;

        new AlertDialog.Builder(this)
                .setTitle("确认重排")
                .setMessage("处理 " + plan.amount + " 个素材（起 " + s + " / 止 " + e + "）\n"
                        + "先写 1 张红底黄字日期分隔图，再按 图视交替 写入相册。\n"
                        + "原片不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", (d, w) -> execute(plan, trimSeconds))
                .show();
    }

    private void execute(Planner.Plan plan, float trimSeconds) {
        running = true;
        progressOverlay.setVisibility(View.VISIBLE);
        txtProgress.setText("准备中…");
        btnRun.setEnabled(false);
        btnRun.setText("处理中…");

        ReorderRunner.run(this, plan, trimSeconds, new ReorderRunner.Callback() {
            @Override
            public void onProgress(String text) {
                txtProgress.setText(text);
            }

            @Override
            public void onDone(String summary) {
                running = false;
                progressOverlay.setVisibility(View.GONE);
                refreshAll();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("完成")
                        .setMessage(summary)
                        .setPositiveButton("好", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                running = false;
                progressOverlay.setVisibility(View.GONE);
                refreshAll();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("出错了")
                        .setMessage(message)
                        .setPositiveButton("好", null)
                        .show();
            }
        });
    }
}
