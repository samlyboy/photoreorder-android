package com.ray.photoreorder;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 执行重排：分隔图 → 逐个素材（图片烧号 / 视频裁头+烧号）→ 按目标时间戳写回相册 */
public class ReorderRunner {

    public interface Callback {
        void onProgress(String text);

        void onDone(String summary);

        void onError(String message);
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    public static void run(Context context, Planner.Plan plan, float trimSeconds, Callback callback) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String dateLine = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                String timeLine = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());

                post(() -> callback.onProgress("写入批次分隔图…"));
                Bitmap separator = BitmapUtil.makeSeparator(dateLine, timeLine, plan.amount);
                MediaStoreWriter.writeBitmap(app, separator,
                        "PR_sep_" + stamp + ".jpg", plan.separatorDate);
                separator.recycle();

                int total = plan.entries.size();
                int done = 0;
                for (Planner.Entry entry : plan.entries) {
                    String label = "处理中 " + (done + 1) + " / " + total
                            + "（编号 " + entry.number + " · " + entry.item.kindLabel() + "）";
                    post(() -> callback.onProgress(label));

                    String baseName = "PR_" + stamp + "_" + entry.number;
                    if (entry.item.video) {
                        File tmp = new File(app.getCacheDir(), baseName + ".mp4");
                        float used = VideoProcessor.process(app, entry.item.uri,
                                entry.number, trimSeconds, tmp);
                        MediaStoreWriter.writeVideo(app, tmp, baseName + ".mp4", entry.dateMillis);
                        //noinspection ResultOfMethodCallIgnored
                        tmp.delete();
                        if (used > 0) {
                            String s = String.format(Locale.US, "编号 %d · 视 · 裁头 %.2fs",
                                    entry.number, used);
                            post(() -> callback.onProgress(s));
                        }
                    } else {
                        Bitmap rendered = BitmapUtil.renderImage(app, entry.item.uri, entry.number);
                        MediaStoreWriter.writeBitmap(app, rendered, baseName + ".jpg", entry.dateMillis);
                        rendered.recycle();
                    }
                    done++;
                }

                int finalDone = done;
                String summary = "完成：1 张分隔图 + " + finalDone + " 个素材，已写入相册 DCIM/"
                        + MediaStoreWriter.ALBUM;
                post(() -> callback.onDone(summary));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                post(() -> callback.onError(msg));
            }
        });
    }

    private static void post(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
