package com.hss01248.camerax;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * TODO: 录像功能暂时禁用，等待 camera-video 模块降级适配后恢复。
 * 原始完整代码见 git 历史记录。
 */
public class CameraxRecordVideoActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    public static final String EXTRA_MAX_DURATION_SECONDS = "extra_max_duration_seconds";
    public static final String EXTRA_PREFER_FRONT_CAMERA = "extra_prefer_front_camera";

    public static void start(Context context, int maxDurationSeconds, boolean preferFrontCamera) {
        // 录像功能暂时禁用
    }

    public static void startForResult(Activity activity, int requestCode, int maxDurationSeconds,
                                      boolean preferFrontCamera) {
        // 录像功能暂时禁用
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
