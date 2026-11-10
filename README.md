# 相册重排 · Android

iPhone 版「相册重排」的安卓移植（不含视频裁头）。独立 App。

- 包名 `com.ray.photoreorder`，最低 Android 10（API 29）
- 产物 `dist/PhotoReorder-v<版本>-b<build>.apk`（已 release 签名，可直接安装）
- 与「返图修复集合安卓版」(`com.ray.fantufix`) 可同时安装，互不覆盖

## 功能

1. **加载相册素材**：读最近 300 个图片/视频，按拍摄时间升序编号（可「加载更多」）
2. **点选取消 / 恢复**：点格子切换选中，未选变暗；右上角红圆 = 烧进画面的编号
3. **起 / 止 区间**：圈定参与重排的范围（闭区间，1 起）；「正序 ↓ / 倒序 ↑」胶囊按钮一键互换起止
4. **重新排列**：先写 1 张红底黄字日期分隔图，再按 图1 视1 图2 视2… 写入相册 `DCIM/PhotoReorder`，每张右上角烧编号；原片一张不删

与「返图修复集合安卓版」的唯一差别：**没有视频裁头输入框**（视频只烧编号，不裁头）。

## 实现要点

| 项 | 做法 |
|---|---|
| 读相册 | `MediaStore` 查询 images + videos，按 `DATE_TAKEN`（无则 `DATE_ADDED`）升序 |
| 写相册 | `MediaStore` insert + `IS_PENDING`，**`DATE_TAKEN` 决定相册排序**；每张时间戳递减 2 秒，编号 1 最新 |
| 图片烧号 | `BitmapFactory`（限制最长边 4096）+ EXIF 摆正 + Canvas 右上角红圆黄字 |
| 视频烧号 | Media3 `Transformer` + `BitmapOverlay`（右上角 overlay），一次转码 |
| 列表 | `RecyclerView` 4 列正方形格，缩略图 `loadThumbnail` + LruCache |

## 构建

```bash
export JAVA_HOME="<JDK 17+>"
export ANDROID_HOME="<Android SDK，需 platform 34 / build-tools 34.0.0>"
gradle assembleRelease      # 或 ./scripts/build.sh
```

签名密钥不在仓库里。首次构建前生成：

```bash
keytool -genkeypair -keystore keys/photoreorder-release.keystore -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 -alias photoreorder \
  -storepass photoreorder2026 -keypass photoreorder2026 -dname "CN=PhotoReorder"
```

## 已知限制

- 相册排序依赖 `DATE_TAKEN`，个别国产 ROM 按 `DATE_MODIFIED` 排序时顺序可能不同
- 视频转码耗时约为时长的 1–3 倍
- 最低 Android 10
