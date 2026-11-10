package com.ray.photoreorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;

/** 角标、图片烧号、红色分隔图 */
public class BitmapUtil {

    public static final int BADGE_RED = 0xFFC71212;
    public static final int BADGE_YELLOW = 0xFFFFE600;

    /** 解码图片（限制最大边，按 EXIF 摆正） */
    public static Bitmap decode(Context context, Uri uri, int maxEdge) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / sample > maxEdge) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap raw;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            raw = BitmapFactory.decodeStream(in, null, opts);
        }
        if (raw == null) throw new IllegalStateException("图片解码失败");

        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(in);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
        }
        return rotate(raw, orientation);
    }

    private static Bitmap rotate(Bitmap src, int orientation) {
        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.postRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.postScale(-1, 1); break;
            default: return src;
        }
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out != src) src.recycle();
        return out;
    }

    /** 图片：摆正 + 右上角烧编号 */
    public static Bitmap renderImage(Context context, Uri uri, int number) throws Exception {
        Bitmap src = decode(context, uri, 4096);
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        if (out == null) out = src;
        if (out != src) src.recycle();

        Canvas canvas = new Canvas(out);
        int w = out.getWidth();
        int h = out.getHeight();
        float r = Math.max(30f, Math.min(w, h) / 14f);
        float margin = r * 0.55f;
        drawBadge(canvas, w - r - margin, r + margin, r, String.valueOf(number));
        return out;
    }

    /** 在画布指定中心画「红圆 + 黄字」 */
    public static void drawBadge(Canvas canvas, float cx, float cy, float radius, String text) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BADGE_RED);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setColor(BADGE_YELLOW);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(radius * 1.18f);
        float baseline = cy - (paint.getFontMetrics().ascent + paint.getFontMetrics().descent) / 2f;
        canvas.drawText(text, cx, baseline, paint);
    }

    /** 视频角标（正方形透明底，供 Media3 overlay 使用） */
    public static Bitmap makeBadgeBitmap(int number) {
        int side = 512;
        Bitmap bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        float r = side * 0.44f;
        drawBadge(canvas, side / 2f, side / 2f, r, String.valueOf(number));
        return bmp;
    }

    /** 红色背景 + 黄字日期时间的批次分隔图（1080×1920） */
    public static Bitmap makeSeparator(String dateLine, String timeLine, int count) {
        int w = 1080, h = 1920;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.parseColor("#C71212"));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(BADGE_YELLOW);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        paint.setTextSize(72f);
        canvas.drawText("分隔 · 批次起点", w / 2f, h / 2f - 200f, paint);

        paint.setTextSize(150f);
        canvas.drawText(dateLine, w / 2f, h / 2f, paint);

        paint.setTextSize(110f);
        canvas.drawText(timeLine, w / 2f, h / 2f + 170f, paint);

        paint.setTextSize(64f);
        canvas.drawText("共 " + count + " 个素材", w / 2f, h / 2f + 330f, paint);
        return bmp;
    }
}
