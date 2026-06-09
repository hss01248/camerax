package com.hss01248.camerax;

/**
 * CameraX 拍照/录像结果回调
 */
public interface CameraxCallback {

    void onSuccess(String filePath);

    default void onCancel() {}

    default void onError(String message) {}
}
