package com.hss01248.screenshotapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.blankj.utilcode.util.ThreadUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.bumptech.glide.Glide;
import com.hss01248.camerax.CameraxCallback;
import com.hss01248.camerax.CameraxCaptureUtil;
import com.hss01248.media.metadata.MetaDataUtil;

/**
 * @Despciption todo
 * @Author hss
 * @Date 5/11/24 11:06 AM
 * @Version 1.0
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERAX_PHOTO = 0x2001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void cameraxTakePhotoWithCallback(View view) {
        CameraxCaptureUtil.takePhoto(this, new CameraxCallback() {
            @Override
            public void onSuccess(String filePath) {
                showCaptureResultDialog(filePath, false);
            }

            @Override
            public void onError(String message) {
                ToastUtils.showShort("拍照失败: " + message);
            }
        });
    }

    public void cameraxTakePhotoFront(View view) {
        CameraxCaptureUtil.takePhoto(this, REQUEST_CAMERAX_PHOTO, true);
    }

    public void cameraxTakePhotoFrontWithCallback(View view) {
        CameraxCaptureUtil.takePhoto(this, new CameraxCallback() {
            @Override
            public void onSuccess(String filePath) {
                showCaptureResultDialog(filePath, false);
            }

            @Override
            public void onError(String message) {
                ToastUtils.showShort("拍照失败: " + message);
            }
        }, true);
    }

    public void cameraxRecordVideoWithCallback(View view) {
        CameraxCaptureUtil.recordVideo(this, new CameraxCallback() {
            @Override
            public void onSuccess(String filePath) {
                showCaptureResultDialog(filePath, true);
            }

            @Override
            public void onError(String message) {
                ToastUtils.showShort("录像失败: " + message);
            }
        });
    }

    public void cameraxRecordVideoFrontWithCallback(View view) {
        CameraxCaptureUtil.recordVideo(this, new CameraxCallback() {
            @Override
            public void onSuccess(String filePath) {
                showCaptureResultDialog(filePath, true);
            }

            @Override
            public void onError(String message) {
                ToastUtils.showShort("录像失败: " + message);
            }
        }, 0, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERAX_PHOTO && resultCode == RESULT_OK) {
            String filePath = CameraxCaptureUtil.getPhotoPath(data);
            if (filePath != null) {
                showCaptureResultDialog(filePath, false);
            }
        }
    }

    private void showCaptureResultDialog(String filePath, boolean isVideo) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        ImageView imageView = new ImageView(this);
        int imgSize = (int) (250 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, imgSize);
        imageView.setLayoutParams(imgParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(imageView);

        if (isVideo) {
            Glide.with(this).asBitmap().load(filePath).into(imageView);
        } else {
            Glide.with(this).load(filePath).into(imageView);
        }

        TextView pathView = new TextView(this);
        pathView.setText(filePath);
        pathView.setTextColor(Color.BLUE);
        pathView.setPadding(0, pad, 0, pad / 2);
        pathView.setTextSize(14);
        layout.addView(pathView);

        TextView metaHint = new TextView(this);
        metaHint.setText("点击上方路径查看 EXIF / Metadata");
        metaHint.setTextColor(Color.GRAY);
        metaHint.setTextSize(12);
        layout.addView(metaHint);

        TextView metaView = new TextView(this);
        metaView.setPadding(0, pad / 2, 0, 0);
        metaView.setTextIsSelectable(true);
        metaView.setTextSize(12);
        layout.addView(metaView);

        pathView.setOnClickListener(v -> {
            ThreadUtils.executeByCached(new ThreadUtils.SimpleTask<String>() {
                @Override
                public String doInBackground() {
                    return MetaDataUtil.getDes(filePath);
                }

                @Override
                public void onSuccess(String result) {
                    metaView.setText(result);
                }
            });
        });

        scrollView.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle(isVideo ? "录像结果" : "拍照结果")
                .setView(scrollView)
                .setPositiveButton("确定", null)
                .show();
    }
}
