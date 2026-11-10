package com.ray.photoreorder;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** 往系统相册写新文件（scoped storage，API 29+） */
public class MediaStoreWriter {

    public static final String ALBUM = "PhotoReorder";

    public static Uri writeBitmap(Context context, Bitmap bitmap, String displayName, long dateMillis)
            throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = baseValues(displayName, "image/jpeg", dateMillis);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("相册插入失败（图片）");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("无法写入图片流");
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            out.flush();
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        publish(context, uri);
        return uri;
    }

    public static Uri writeVideo(Context context, File file, String displayName, long dateMillis)
            throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = baseValues(displayName, "video/mp4", dateMillis);
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("相册插入失败（视频）");
        try (OutputStream out = resolver.openOutputStream(uri);
             FileInputStream in = new FileInputStream(file)) {
            if (out == null) throw new IllegalStateException("无法写入视频流");
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        publish(context, uri);
        return uri;
    }

    private static ContentValues baseValues(String displayName, String mime, long dateMillis) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DCIM + "/" + ALBUM);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        // 相册排序完全靠这个字段：DATE_TAKEN 优先，DATE_ADDED 兜底
        values.put(MediaStore.MediaColumns.DATE_TAKEN, dateMillis);
        values.put(MediaStore.MediaColumns.DATE_ADDED, dateMillis / 1000L);
        return values;
    }

    private static void publish(Context context, Uri uri) {
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, done, null, null);
    }
}
