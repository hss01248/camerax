package com.hss01248.camerax;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.blankj.utilcode.util.CloseUtils;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.LogUtils;
import com.hss01248.media.metadata.ExifUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 将 JPEG 像素按 EXIF orientation 旋转/翻转后写回，并保留其余 EXIF，orientation 归零。
 */
final class PhotoExifNormalizer {

    private PhotoExifNormalizer() {
    }

    static boolean normalizeOrientationInPlace(File file, int jpegQuality, boolean frontCamera) {
        if (file == null || !file.exists() || file.length() == 0) {
            return false;
        }
        String path = file.getAbsolutePath();
        File tmp = null;
        try {
            ExifInterface srcExif = new ExifInterface(path);
            int orientation = srcExif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            boolean hasExifHorizontalFlip = hasExifHorizontalFlip(orientation);
            boolean needsExifTransform = orientation != ExifInterface.ORIENTATION_NORMAL;
            // 前置镜像优先由 takePhoto 里 Metadata.setReversedHorizontal 写入 EXIF；未写入时再像素级翻转
            boolean needsFrontMirror = frontCamera && !hasExifHorizontalFlip;
            if (!needsExifTransform && !needsFrontMirror) {
                return true;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src = BitmapFactory.decodeFile(path, options);
            if (src == null) {
                LogUtils.w("decode photo failed: " + path);
                return false;
            }

            int srcW = src.getWidth();
            int srcH = src.getHeight();
            Bitmap transformed = src;
            if (needsExifTransform) {
                transformed = applyExifOrientation(src, orientation);
                if (transformed != src) {
                    src.recycle();
                }
            }
            if (needsFrontMirror) {
                Bitmap mirrored = mirrorHorizontal(transformed);
                if (mirrored != transformed) {
                    if (transformed != src) {
                        transformed.recycle();
                    }
                    transformed = mirrored;
                }
            }

            tmp = new File(file.getParentFile(), "tmp-orient-" + file.getName());
            if (tmp.exists() && !tmp.delete()) {
                transformed.recycle();
                return false;
            }

            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(tmp);
                boolean compressed = transformed.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fos);
                fos.flush();
                if (!compressed || !tmp.exists() || tmp.length() == 0) {
                    LogUtils.w("write rotated jpeg failed: " + path);
                    tmp.delete();
                    transformed.recycle();
                    return false;
                }
            } finally {
                CloseUtils.closeIO(fos);
                transformed.recycle();
            }

            if (!copyExif(path, tmp.getAbsolutePath())) {
                LogUtils.w("copy exif failed: " + path);
                tmp.delete();
                return false;
            }

            if (!resetOrientationAndSize(tmp.getAbsolutePath(), orientation, srcW, srcH)) {
                LogUtils.w("reset orientation failed: " + path);
                tmp.delete();
                return false;
            }

            if (!replaceFile(file, tmp)) {
                LogUtils.w("replace photo failed: " + path);
                tmp.delete();
                return false;
            }
            return true;
        } catch (Throwable e) {
            LogUtils.w("normalize photo orientation failed: " + path, e);
            if (tmp != null && tmp.exists()) {
                tmp.delete();
            }
            return false;
        }
    }

    private static boolean copyExif(String srcPath, String dstPath) {
       /* if (ExifApi.copyAllExif(srcPath, dstPath)) {
            return true;
        }*/
        try {
            ExifUtil.copyExif(srcPath, dstPath);
            return new File(dstPath).exists() && new File(dstPath).length() > 0;
        } catch (Throwable e) {
            LogUtils.w("fallback copyExif failed: " + srcPath, e);
            return false;
        }
    }

    private static boolean resetOrientationAndSize(String path, int orientation, int srcW, int srcH)
            throws IOException {
        ExifInterface exif = new ExifInterface(path);
        exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                String.valueOf(ExifInterface.ORIENTATION_NORMAL));
        if (isDimensionSwapped(orientation)) {
            exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, String.valueOf(srcH));
            exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, String.valueOf(srcW));
            exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, String.valueOf(srcH));
            exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, String.valueOf(srcW));
        }
        exif.saveAttributes();
        return true;
    }

    private static boolean hasExifHorizontalFlip(int orientation) {
        return orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL
                || orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    private static Bitmap mirrorHorizontal(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.setScale(-1, 1);
        return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static boolean isDimensionSwapped(int orientation) {
        return orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_ROTATE_270
                || orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE;
    }

    private static boolean replaceFile(File target, File tmp) {
        if (!tmp.exists() || tmp.length() == 0) {
            return false;
        }
        if (target.exists() && !target.delete()) {
            return false;
        }
        if (tmp.renameTo(target)) {
            return target.exists() && target.length() > 0;
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(tmp);
            boolean copied = FileIOUtils.writeFileFromIS(target, in);
            if (copied) {
                tmp.delete();
            }
            return copied && target.exists() && target.length() > 0;
        } catch (IOException e) {
            LogUtils.w("replaceFile copy failed: " + target.getAbsolutePath(), e);
            return false;
        } finally {
            CloseUtils.closeIO(in);
        }
    }

    @Nullable
    private static Bitmap applyExifOrientation(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(270);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(270);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
