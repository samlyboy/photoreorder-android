package com.ray.photoreorder;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 读取系统相册（MediaStore），图片 + 视频合并，按拍摄时间升序 */
public class MediaRepo {

    public static List<MediaItem> load(Context context, int limit) {
        List<MediaItem> all = new ArrayList<>();
        all.addAll(query(context, true, limit));
        all.addAll(query(context, false, limit));
        Collections.sort(all, new Comparator<MediaItem>() {
            @Override
            public int compare(MediaItem a, MediaItem b) {
                return Long.compare(a.dateMillis, b.dateMillis);
            }
        });
        return all;
    }

    private static List<MediaItem> query(Context context, boolean images, int limit) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = images
                ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        String[] projection = new String[]{
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
        };

        List<MediaItem> list = new ArrayList<>();
        try (Cursor c = resolver.query(collection, projection, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (c == null) return list;
            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN);
            int addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);

            while (c.moveToNext() && list.size() < limit) {
                long id = c.getLong(idCol);
                String name = c.getString(nameCol);
                String mime = c.getString(mimeCol);
                long taken = c.isNull(takenCol) ? 0 : c.getLong(takenCol);
                long added = c.isNull(addedCol) ? 0 : c.getLong(addedCol) * 1000L;
                long date = taken > 0 ? taken : added;
                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                list.add(new MediaItem(id, uri, name, mime, !images, date, 0));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 视频时长（毫秒），失败返回 0 */
    public static long videoDuration(Context context, Uri uri) {
        try (android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever()) {
            r.setDataSource(context, uri);
            String d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? 0 : Long.parseLong(d);
        } catch (Exception e) {
            return 0;
        }
    }
}
