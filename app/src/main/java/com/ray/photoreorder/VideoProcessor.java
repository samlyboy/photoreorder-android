package com.ray.photoreorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.TextureOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 视频：裁头 + 右上角烧编号，一次重编码（Media3 Transformer） */
public class VideoProcessor {

    public static final float DEFAULT_TRIM = 0.05f;
    public static final float MAX_TRIM = 10f;

    /** 返回实际使用的裁头秒数（0 表示没裁） */
    public static float process(Context context, Uri source, int number,
                                float trimSeconds, File outFile) throws Exception {
        long[] size = videoSize(context, source);
        Bitmap badge = BitmapUtil.makeBadgeBitmap(number);

        float trim = clampTrim(trimSeconds);
        long durationMs = MediaRepo.videoDuration(context, source);
        if (durationMs > 0 && durationMs - (long) (trim * 1000) < 300) {
            trim = 0f; // 太短，裁了就没了：放弃裁头，只烧号
        }

        float scaleX = 0.18f;
        float scaleY = size[1] > 0 ? scaleX * ((float) size[0] / (float) size[1]) : scaleX;
        OverlaySettings settings = new OverlaySettings.Builder()
                .setBackgroundFrameAnchor(0.84f, -0.80f)
                .setOverlayFrameAnchor(0f, 0f)
                .setScale(scaleX, scaleY)
                .build();

        ImmutableList<TextureOverlay> overlays = ImmutableList.of(
                (TextureOverlay) BitmapOverlay.createStaticBitmapOverlay(badge, settings));
        List<Effect> videoEffects = Collections.<Effect>singletonList(
                new OverlayEffect(overlays));

        MediaItem.Builder mediaBuilder = new MediaItem.Builder().setUri(source);
        if (trim > 0) {
            mediaBuilder.setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((long) (trim * 1000))
                            .setStartsAtKeyFrame(false)
                            .build());
        }

        EditedMediaItem edited = new EditedMediaItem.Builder(mediaBuilder.build())
                .setEffects(new Effects(Collections.<AudioProcessor>emptyList(), videoEffects))
                .build();

        if (outFile.exists() && !outFile.delete()) {
            throw new IllegalStateException("无法清理临时视频文件");
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            try {
                new Transformer.Builder(context)
                        .addListener(new Transformer.Listener() {
                            @Override
                            public void onCompleted(Composition composition, ExportResult result) {
                                latch.countDown();
                            }

                            @Override
                            public void onError(Composition composition, ExportResult result,
                                                ExportException exception) {
                                error.set(exception);
                                latch.countDown();
                            }
                        })
                        .build()
                        .start(edited, outFile.getAbsolutePath());
            } catch (Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        if (!latch.await(30, TimeUnit.MINUTES)) {
            throw new IllegalStateException("视频处理超时");
        }
        if (error.get() != null) {
            throw new IllegalStateException("视频处理失败：" + error.get().getMessage(), error.get());
        }
        return trim;
    }

    private static float clampTrim(float value) {
        if (Float.isNaN(value) || value < 0) return DEFAULT_TRIM;
        return Math.min(value, MAX_TRIM);
    }

    /** 返回 {显示宽, 显示高}（已按旋转角交换） */
    private static long[] videoSize(Context context, Uri uri) {
        long w = 1080, h = 1920;
        int rotation = 0;
        try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
            r.setDataSource(context, uri);
            String ww = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hh = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (ww != null) w = Long.parseLong(ww);
            if (hh != null) h = Long.parseLong(hh);
            if (rr != null) rotation = Integer.parseInt(rr);
        } catch (Exception ignored) {
        }
        if (rotation == 90 || rotation == 270) {
            long t = w;
            w = h;
            h = t;
        }
        return new long[]{w, h};
    }
}
