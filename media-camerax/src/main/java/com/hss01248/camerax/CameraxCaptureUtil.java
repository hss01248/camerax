package com.hss01248.camerax;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * CameraX 拍照/录像 统一入口工具类
 */
public class CameraxCaptureUtil {

    public static final int REQUEST_CODE_TAKE_PHOTO = 0x1001;
    public static final int REQUEST_CODE_RECORD_VIDEO = 0x1002;

    static CameraxCallback pendingPhotoCallback;
    static CameraxCallback pendingVideoCallback;

    // ========== 带回调的方法 ==========

    /**
     * 拍照（带回调，回调返回文件路径），默认后置摄像头
     */
    public static void takePhoto(Context context, CameraxCallback callback) {
        takePhoto(context, callback, false);
    }

    /**
     * 拍照（带回调，回调返回文件路径）
     *
     * @param preferFrontCamera 是否优先使用前置摄像头
     */
    public static void takePhoto(Context context, CameraxCallback callback,
                                 boolean preferFrontCamera) {
        pendingPhotoCallback = callback;
        CameraxTakePhotoActivity.start(context, preferFrontCamera);
    }

    /**
     * 录像（带回调，回调返回文件路径），无最大时长限制，默认后置摄像头
     */
    public static void recordVideo(Context context, CameraxCallback callback) {
        recordVideo(context, callback, 0);
    }

    /**
     * 录像（带回调，回调返回文件路径），默认后置摄像头
     *
     * @param maxDurationSeconds 最大录制时长（秒），小于等于 0 表示不限制
     */
    public static void recordVideo(Context context, CameraxCallback callback,
                                   int maxDurationSeconds) {
        recordVideo(context, callback, maxDurationSeconds, false);
    }

    /**
     * 录像（带回调，回调返回文件路径）
     *
     * @param maxDurationSeconds  最大录制时长（秒），小于等于 0 表示不限制
     * @param preferFrontCamera   是否优先使用前置摄像头
     */
    public static void recordVideo(Context context, CameraxCallback callback,
                                   int maxDurationSeconds, boolean preferFrontCamera) {
        pendingVideoCallback = callback;
        CameraxRecordVideoActivity.start(context, maxDurationSeconds, preferFrontCamera);
    }

    // ========== 基于 startActivityForResult 的方法 ==========

    public static void takePhoto(Activity activity) {
        takePhoto(activity, REQUEST_CODE_TAKE_PHOTO, false);
    }

    public static void takePhoto(Activity activity, int requestCode) {
        takePhoto(activity, requestCode, false);
    }

    public static void takePhoto(Activity activity, int requestCode, boolean preferFrontCamera) {
        CameraxTakePhotoActivity.startForResult(activity, requestCode, preferFrontCamera);
    }

    public static void recordVideo(Activity activity) {
        recordVideo(activity, REQUEST_CODE_RECORD_VIDEO, 0);
    }

    public static void recordVideo(Activity activity, int requestCode) {
        recordVideo(activity, requestCode, 0);
    }

    public static void recordVideo(Activity activity, int requestCode, int maxDurationSeconds) {
        recordVideo(activity, requestCode, maxDurationSeconds, false);
    }

    public static void recordVideo(Activity activity, int requestCode, int maxDurationSeconds,
                                   boolean preferFrontCamera) {
        CameraxRecordVideoActivity.startForResult(activity, requestCode, maxDurationSeconds,
                preferFrontCamera);
    }

    // ========== 无回调的启动方法 ==========

    public static void openTakePhoto(Context context) {
        openTakePhoto(context, false);
    }

    public static void openTakePhoto(Context context, boolean preferFrontCamera) {
        CameraxTakePhotoActivity.start(context, preferFrontCamera);
    }

    public static void openRecordVideo(Context context) {
        openRecordVideo(context, 0);
    }

    public static void openRecordVideo(Context context, int maxDurationSeconds) {
        openRecordVideo(context, maxDurationSeconds, false);
    }

    public static void openRecordVideo(Context context, int maxDurationSeconds,
                                       boolean preferFrontCamera) {
        CameraxRecordVideoActivity.start(context, maxDurationSeconds, preferFrontCamera);
    }

    // ========== 从 onActivityResult 提取结果 ==========

    public static String getPhotoPath(Intent data) {
        if (data == null) return null;
        return data.getStringExtra(CameraxTakePhotoActivity.EXTRA_PHOTO_PATH);
    }

    public static String getVideoPath(Intent data) {
        if (data == null) return null;
        return data.getStringExtra(CameraxRecordVideoActivity.EXTRA_VIDEO_PATH);
    }

    // ========== 内部回调分发 ==========

    static void notifyPhotoSuccess(String path) {
        CameraxCallback cb = pendingPhotoCallback;
        pendingPhotoCallback = null;
        if (cb != null) cb.onSuccess(path);
    }

    static void notifyPhotoCancel() {
        CameraxCallback cb = pendingPhotoCallback;
        pendingPhotoCallback = null;
        if (cb != null) cb.onCancel();
    }

    static void notifyPhotoError(String msg) {
        CameraxCallback cb = pendingPhotoCallback;
        pendingPhotoCallback = null;
        if (cb != null) cb.onError(msg);
    }

    static void notifyVideoSuccess(String path) {
        CameraxCallback cb = pendingVideoCallback;
        pendingVideoCallback = null;
        if (cb != null) cb.onSuccess(path);
    }

    static void notifyVideoCancel() {
        CameraxCallback cb = pendingVideoCallback;
        pendingVideoCallback = null;
        if (cb != null) cb.onCancel();
    }

    static void notifyVideoError(String msg) {
        CameraxCallback cb = pendingVideoCallback;
        pendingVideoCallback = null;
        if (cb != null) cb.onError(msg);
    }
}
