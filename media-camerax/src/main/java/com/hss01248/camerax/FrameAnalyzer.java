package com.hss01248.camerax;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

/**
 * @Despciption todo
 * @Author hss
 * @Date 9/12/25 5:37 PM
 * @Version 1.0
 */
// 帧分析器类，用于处理逐帧回调
public class FrameAnalyzer implements ImageAnalysis.Analyzer {
    private static final String TAG = "frm";
    private long lastAnalyzedTimestamp = 0;

    @Override
    public void analyze(@NonNull ImageProxy image) {
        long currentTimestamp = System.currentTimeMillis();

        // 可以限制分析频率，例如每秒分析一次
        if (currentTimestamp - lastAnalyzedTimestamp >= 1000) {
            // 处理当前帧
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                processFrame(image);
            }
            lastAnalyzedTimestamp = currentTimestamp;
        }

        // 重要：处理完图像后必须关闭它，否则会导致相机卡住
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            image.close();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void processFrame(ImageProxy image) {
        // 在这里处理帧数据
        int rotationDegrees = image.getImageInfo().getRotationDegrees();
        int width = image.getWidth();
        int height = image.getHeight();

        Log.d(TAG, "处理帧 - 宽: " + width + ", 高: " + height + ", 旋转角度: " + rotationDegrees);

        // 如果需要获取图像像素数据，可以使用以下代码
            /*
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            // 这里可以对data进行处理，例如进行图像处理、分析等
            */

        // 可以在这里添加自定义的帧处理逻辑
        // 例如：人脸检测、条形码识别、颜色分析等
    }
}
