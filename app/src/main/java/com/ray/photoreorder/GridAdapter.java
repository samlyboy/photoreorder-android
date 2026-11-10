package com.ray.photoreorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 素材网格：正方形格子 + 左上原始序号 + 右上最终编号 + 点选取消/恢复 */
public class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {

    public interface OnToggle {
        void onToggle(int position);
    }

    private final Context context;
    private final List<MediaItem> items;
    private final int cellSize;
    private final OnToggle toggle;
    private final ExecutorService thumbPool = Executors.newFixedThreadPool(3);
    private final LruCache<Long, Bitmap> cache =
            new LruCache<Long, Bitmap>(32 * 1024 * 1024) {
                @Override
                protected int sizeOf(Long key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    public GridAdapter(Context context, List<MediaItem> items, int spanCount, OnToggle toggle) {
        this.context = context;
        this.items = items;
        this.toggle = toggle;
        int width = context.getResources().getDisplayMetrics().widthPixels;
        // RecyclerView 左右各 10dp padding，每格四周 2dp margin
        int usable = width - dp(20) - dp(4) * spanCount;
        this.cellSize = Math.max(dp(48), usable / spanCount);
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 图片和视频的 MediaStore id 来自不同表，可能重号，用符号区分 */
    private long key(MediaItem item) {
        return item.video ? -item.id : item.id;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(cellSize, cellSize);
        int m = dp(2);
        lp.setMargins(m, m, m, m);
        view.setLayoutParams(lp);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaItem item = items.get(position);
        holder.origin.setText(String.valueOf(position + 1));

        Integer number = item.number > 0 ? item.number : null;
        if (number != null && item.selected) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(String.valueOf(number));
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        holder.scrim.setVisibility(item.selected ? View.GONE : View.VISIBLE);
        holder.image.setAlpha(item.selected ? 1f : 0.35f);
        holder.videoMark.setVisibility(item.video ? View.VISIBLE : View.GONE);

        long k = key(item);
        holder.image.setTag(k);
        Bitmap cached = cache.get(k);
        if (cached != null) {
            holder.image.setImageBitmap(cached);
        } else {
            holder.image.setImageDrawable(null);
            thumbPool.execute(() -> {
                Bitmap bmp = null;
                try {
                    bmp = context.getContentResolver()
                            .loadThumbnail(item.uri, new Size(cellSize, cellSize), null);
                    cache.put(k, bmp);
                } catch (Exception ignored) {
                }
                Bitmap result = bmp;
                if (result != null) {
                    holder.image.post(() -> {
                        if (k == (Long) holder.image.getTag()) {
                            holder.image.setImageBitmap(result);
                        }
                    });
                }
            });
        }

        holder.itemView.setOnClickListener(v -> toggle.onToggle(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView origin;
        final TextView badge;
        final View scrim;
        final TextView videoMark;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            origin = itemView.findViewById(R.id.origin);
            badge = itemView.findViewById(R.id.badge);
            scrim = itemView.findViewById(R.id.scrim);
            videoMark = itemView.findViewById(R.id.videoMark);
        }
    }
}
