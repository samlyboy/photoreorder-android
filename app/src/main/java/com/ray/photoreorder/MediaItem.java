package com.ray.photoreorder;

import android.net.Uri;

/** 相册里的一条素材（图片或视频） */
public class MediaItem {
    public final long id;
    public final Uri uri;
    public final String name;
    public final String mimeType;
    public final boolean video;
    public final long dateMillis;
    public final long durationMs;
    public boolean selected = true;
    /** 烧进画面的编号（0 = 不在当前区间内，不显示） */
    public int number = 0;

    public MediaItem(long id, Uri uri, String name, String mimeType,
                     boolean video, long dateMillis, long durationMs) {
        this.id = id;
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.video = video;
        this.dateMillis = dateMillis;
        this.durationMs = durationMs;
    }

    public String kindLabel() {
        return video ? "视" : "图";
    }
}
