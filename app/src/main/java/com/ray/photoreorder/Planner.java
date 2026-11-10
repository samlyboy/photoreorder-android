package com.ray.photoreorder;

import java.util.ArrayList;
import java.util.List;

/**
 * 重排计划：
 *  - number：逐格位次编号（起 → 止 方向每格 +1，1、2、3… 连排），烧进画面
 *  - writeOrder：写进相册的先后（图1 视1 图2 视2… 交错）
 */
public class Planner {

    public static class Entry {
        public final MediaItem item;
        public final int number;      // 烧进画面的编号（逐格位次）
        public final int writeIndex;  // 相册写入先后（0 起）
        public final long dateMillis; // 写入相册的时间戳

        Entry(MediaItem item, int number, int writeIndex, long dateMillis) {
            this.item = item;
            this.number = number;
            this.writeIndex = writeIndex;
            this.dateMillis = dateMillis;
        }
    }

    public static class Plan {
        public final List<Entry> entries = new ArrayList<>();  // 按相册写入先后
        public final long separatorDate;
        public final int amount;

        Plan(long separatorDate, int amount) {
            this.separatorDate = separatorDate;
            this.amount = amount;
        }
    }

    /** 已选序列里的区间（闭区间，1 起，起可大于止＝倒序） */
    public static List<MediaItem> range(List<MediaItem> selected, int start, int end) {
        List<MediaItem> out = new ArrayList<>();
        int n = selected.size();
        if (n == 0) return out;
        start = Math.max(1, Math.min(start, n));
        end = Math.max(1, Math.min(end, n));
        int step = start <= end ? 1 : -1;
        for (int i = start; ; i += step) {
            out.add(selected.get(i - 1));
            if (i == end) break;
        }
        return out;
    }

    public static Plan make(List<MediaItem> selected, int start, int end, long nowMillis) {
        List<MediaItem> between = range(selected, start, end);

        // 1) 逐格位次编号：按区间方向 1、2、3…
        // 2) 交错：抽成图片序列 + 视频序列后交替，决定写入先后
        List<MediaItem> interleaved = interleave(between);
        Plan plan = new Plan(nowMillis, between.size());

        for (int i = 0; i < interleaved.size(); i++) {
            MediaItem item = interleaved.get(i);
            int number = positionOf(between, item) + 1;
            long date = nowMillis - 2000L * (i + 1);
            plan.entries.add(new Entry(item, number, i, date));
        }
        return plan;
    }

    /** 每个素材在区间里的位次（0 起），用于查编号 */
    private static int positionOf(List<MediaItem> between, MediaItem item) {
        for (int i = 0; i < between.size(); i++) {
            if (between.get(i).id == item.id) return i;
        }
        return 0;
    }

    private static List<MediaItem> interleave(List<MediaItem> src) {
        List<MediaItem> images = new ArrayList<>();
        List<MediaItem> videos = new ArrayList<>();
        for (MediaItem m : src) {
            if (m.video) videos.add(m); else images.add(m);
        }
        List<MediaItem> out = new ArrayList<>(src.size());
        int i = 0, v = 0;
        while (i < images.size() || v < videos.size()) {
            if (i < images.size()) out.add(images.get(i++));
            if (v < videos.size()) out.add(videos.get(v++));
        }
        return out;
    }
}
