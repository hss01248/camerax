package com.hss01248.camerax;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.AspectRatio;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.exifinterface.media.ExifInterface;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.google.common.util.concurrent.ListenableFuture;
import com.hss01248.media.camerax.R;
import com.hss01248.permission.MyPermissions;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraxTakePhotoActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO_PATH = "extra_photo_path";
    public static final String EXTRA_PREFER_FRONT_CAMERA = "extra_prefer_front_camera";

    private static final int FLASH_OFF = 0;
    private static final int FLASH_ON = 1;
    private static final int FLASH_AUTO = 2;

    private static final int RATIO_1_1 = 0;
    private static final int RATIO_3_4 = 1;
    private static final int RATIO_9_16 = 2;
    private static final int RATIO_FULL = 3;
    private static final int JPEG_QUALITY = 84;

    private PreviewView previewView;
    private ImageButton btnCapture, btnSwitchCamera, btnClose;
    private ProgressBar captureProgress;
    private ImageButton btnFlash, btnSettings;
    private LinearLayout topSection, topBar, settingsPanel, bottomBar, previewBottomBar;
    private TextView btnRatio1_1, btnRatio3_4, btnRatio9_16, btnRatioFull;
    private FrameLayout previewOverlay;
    private ImageView ivPreview;
    private ImageButton btnPreviewCancel, btnPreviewConfirm;

    private Preview preview;
    private ImageCapture imageCapture;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private OrientationEventListener orientationEventListener;
    private int currentSurfaceRotation = Surface.ROTATION_0;
    private Insets systemBarInsets = Insets.NONE;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = FLASH_OFF;
    private int currentRatio = RATIO_3_4;
    private String lastPhotoPath;

    public static void start(Context context) {
        start(context, false);
    }

    public static void start(Context context, boolean preferFrontCamera) {
        Intent intent = new Intent(context, CameraxTakePhotoActivity.class);
        intent.putExtra(EXTRA_PREFER_FRONT_CAMERA, preferFrontCamera);
        context.startActivity(intent);
    }

    public static void startForResult(Activity activity, int requestCode) {
        startForResult(activity, requestCode, false);
    }

    public static void startForResult(Activity activity, int requestCode, boolean preferFrontCamera) {
        Intent intent = new Intent(activity, CameraxTakePhotoActivity.class);
        intent.putExtra(EXTRA_PREFER_FRONT_CAMERA, preferFrontCamera);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camerax_take_photo);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        initViews();
        setupWindowInsets();
        setupListeners();
        initOrientationListener();
        applyInitialLensFacing();

        btnCapture.setVisibility(View.GONE);
        btnSwitchCamera.setVisibility(View.GONE);

        updateFlashIcon();
        updateRatioSelection();

        requestPermissionAndStart();
    }

    private void applyInitialLensFacing() {
        if (getIntent().getBooleanExtra(EXTRA_PREFER_FRONT_CAMERA, false)) {
            lensFacing = CameraSelector.LENS_FACING_FRONT;
            flashMode = FLASH_OFF;
        }
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        btnCapture = findViewById(R.id.btnCapture);
        captureProgress = findViewById(R.id.captureProgress);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnClose = findViewById(R.id.btnClose);
        btnFlash = findViewById(R.id.btnFlash);
        btnSettings = findViewById(R.id.btnSettings);
        topSection = findViewById(R.id.topSection);
        topBar = findViewById(R.id.topBar);
        settingsPanel = findViewById(R.id.settingsPanel);
        previewBottomBar = findViewById(R.id.previewBottomBar);
        bottomBar = findViewById(R.id.bottomBar);
        btnRatio1_1 = findViewById(R.id.btnRatio1_1);
        btnRatio3_4 = findViewById(R.id.btnRatio3_4);
        btnRatio9_16 = findViewById(R.id.btnRatio9_16);
        btnRatioFull = findViewById(R.id.btnRatioFull);
        previewOverlay = findViewById(R.id.previewOverlay);
        ivPreview = findViewById(R.id.ivPreview);
        btnPreviewCancel = findViewById(R.id.btnPreviewCancel);
        btnPreviewConfirm = findViewById(R.id.btnPreviewConfirm);
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(topSection, (v, insets) -> {
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topSection.setPadding(0, systemBarInsets.top, 0, 0);
            applyControlBarPadding();
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(previewBottomBar, (v, insets) -> {
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            applyControlBarPadding();
            return insets;
        });
        ViewCompat.requestApplyInsets(topSection);
    }

    private void applyControlBarPadding() {
        int bottom = systemBarInsets.bottom + dp2px(16);
       // bottomBar.setPadding(dp2px(32), dp2px(16), dp2px(32), bottom);
       // previewBottomBar.setPadding(dp2px(32), dp2px(16), dp2px(32), bottom);
    }

    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> {
            CameraxCaptureUtil.notifyPhotoCancel();
            setResult(RESULT_CANCELED);
            finish();
        });

        btnSwitchCamera.setOnClickListener(v -> {
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                flashMode = FLASH_OFF;
                updateFlashIcon();
            }
            startCamera();
        });

        btnCapture.setOnClickListener(v -> takePhoto());

        btnFlash.setOnClickListener(v -> {
            flashMode = (flashMode + 1) % 3;
            updateFlashIcon();
            applyFlashMode();
        });

        btnSettings.setOnClickListener(v -> {
            boolean visible = settingsPanel.getVisibility() == View.VISIBLE;
            settingsPanel.setVisibility(visible ? View.GONE : View.VISIBLE);
        });

        View.OnClickListener ratioClick = v -> {
            int id = v.getId();
            int newRatio;
            if (id == R.id.btnRatio1_1) {
                newRatio = RATIO_1_1;
            } else if (id == R.id.btnRatio9_16) {
                newRatio = RATIO_9_16;
            } else if (id == R.id.btnRatioFull) {
                newRatio = RATIO_FULL;
            } else {
                newRatio = RATIO_3_4;
            }
            if (newRatio != currentRatio) {
                currentRatio = newRatio;
                updateRatioSelection();
                settingsPanel.setVisibility(View.GONE);
                startCamera();
            } else {
                settingsPanel.setVisibility(View.GONE);
            }
        };
        btnRatio1_1.setOnClickListener(ratioClick);
        btnRatio3_4.setOnClickListener(ratioClick);
        btnRatio9_16.setOnClickListener(ratioClick);
        btnRatioFull.setOnClickListener(ratioClick);

        btnPreviewCancel.setOnClickListener(v -> {
            if (lastPhotoPath != null) {
                new File(lastPhotoPath).delete();
                lastPhotoPath = null;
            }
            previewOverlay.setVisibility(View.GONE);
        });

        btnPreviewConfirm.setOnClickListener(v -> {
            if (lastPhotoPath != null) {
                //ToastUtils.showLong(getString(R.string.cam_photo_saved, lastPhotoPath));
                CameraxCaptureUtil.notifyPhotoSuccess(lastPhotoPath);
                Intent data = new Intent();
                data.putExtra(EXTRA_PHOTO_PATH, lastPhotoPath);
                setResult(RESULT_OK, data);
            }
            finish();
        });
    }

    private void updateFlashIcon() {
        switch (flashMode) {
            case FLASH_ON:
                btnFlash.setImageResource(R.drawable.ic_cam_flash_on);
                btnFlash.setContentDescription("flash on");
                break;
            case FLASH_AUTO:
                btnFlash.setImageResource(R.drawable.ic_cam_flash_auto);
                btnFlash.setContentDescription("flash auto");
                break;
            default:
                btnFlash.setImageResource(R.drawable.ic_cam_flash_off);
                btnFlash.setContentDescription("flash off");
                break;
        }
    }

    private void applyFlashMode() {
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            btnFlash.setVisibility(View.GONE);
            flashMode = FLASH_OFF;
            updateFlashIcon();
            if (imageCapture != null) {
                imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
            if (camera != null) {
                camera.getCameraControl().enableTorch(false);
            }
            return;
        }

        btnFlash.setVisibility(View.VISIBLE);

        if (imageCapture != null) {
            switch (flashMode) {
                case FLASH_ON:
                    imageCapture.setFlashMode(ImageCapture.FLASH_MODE_ON);
                    break;
                case FLASH_AUTO:
                    imageCapture.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
                    break;
                default:
                    imageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
                    break;
            }
        }
        if (camera == null) {
            return;
        }
        boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
        btnFlash.setEnabled(hasFlash);
        btnFlash.setAlpha(hasFlash ? 1f : 0.4f);
        if (!hasFlash) {
            if (flashMode == FLASH_ON) {
                flashMode = FLASH_OFF;
                updateFlashIcon();
            }
            camera.getCameraControl().enableTorch(false);
            return;
        }
        boolean torchOn = (flashMode == FLASH_ON);
        ListenableFuture<Void> torchFuture = camera.getCameraControl().enableTorch(torchOn);
        torchFuture.addListener(() -> {
            try {
                torchFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                LogUtils.w("torch failed: " + e.getMessage());
                runOnUiThread(() -> ToastUtils.showShort(R.string.cam_torch_control_failed));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updateRatioSelection() {
        int selectedColor = 0xFFFFCC00;
        int normalColor = 0xFF666666;

        setRatioBtnStyle(btnRatio1_1, currentRatio == RATIO_1_1 ? selectedColor : normalColor);
        setRatioBtnStyle(btnRatio3_4, currentRatio == RATIO_3_4 ? selectedColor : normalColor);
        setRatioBtnStyle(btnRatio9_16, currentRatio == RATIO_9_16 ? selectedColor : normalColor);
        setRatioBtnStyle(btnRatioFull, currentRatio == RATIO_FULL ? selectedColor : normalColor);
    }

    private void setRatioBtnStyle(TextView tv, int bgColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(16f);
        gd.setColor(bgColor);
        tv.setBackground(gd);
        tv.setTextColor(bgColor == 0xFFFFCC00 ? Color.BLACK : Color.WHITE);
    }

    private void initOrientationListener() {
        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;
                }
                applyTargetRotation(orientationToSurfaceRotation(orientation));
            }
        };
    }

    private static int orientationToSurfaceRotation(int orientation) {
        if (orientation >= 45 && orientation < 135) {
            return Surface.ROTATION_270;
        } else if (orientation >= 135 && orientation < 225) {
            return Surface.ROTATION_180;
        } else if (orientation >= 225 && orientation < 315) {
            return Surface.ROTATION_90;
        }
        return Surface.ROTATION_0;
    }

    private void applyTargetRotation(int rotation) {
        if (currentSurfaceRotation == rotation) {
            return;
        }
        currentSurfaceRotation = rotation;
        if (preview != null) {
            preview.setTargetRotation(rotation);
        }
        if (imageCapture != null) {
            imageCapture.setTargetRotation(rotation);
        }
        applyOrientationDependentViews(rotation);
    }

    /** 按钮位置不动，仅旋转图标；确认页大图随设备方向旋转 */
    private void applyOrientationDependentViews(int surfaceRotation) {
        float degrees = surfaceRotationToDegrees(surfaceRotation);
        rotateViewForOrientation(btnClose, degrees);
        rotateViewForOrientation(btnFlash, degrees);
        rotateViewForOrientation(btnSwitchCamera, degrees);
        rotateViewForOrientation(btnPreviewCancel, degrees);
        rotateViewForOrientation(btnPreviewConfirm, degrees);
        if (previewOverlay != null && previewOverlay.getVisibility() == View.VISIBLE) {
            rotateViewForOrientation(ivPreview, degrees);
        }
    }

    private static void rotateViewForOrientation(View view, float degrees) {
        if (view != null) {
            view.setRotation(degrees);
        }
    }

    private static float surfaceRotationToDegrees(int surfaceRotation) {
        switch (surfaceRotation) {
            case Surface.ROTATION_90:
                return 90f;
            case Surface.ROTATION_180:
                return 180f;
            case Surface.ROTATION_270:
                return 270f;
            default:
                return 0f;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }
    }

    @Override
    protected void onStop() {
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        super.onStop();
    }

    private void requestPermissionAndStart() {
        MyPermissions.requestByMostEffort(false, true, new PermissionUtils.FullCallback() {
            @Override
            public void onGranted(@NonNull List<String> granted) {
                btnCapture.setVisibility(View.VISIBLE);
                btnSwitchCamera.setVisibility(View.VISIBLE);
                startCamera();
            }

            @Override
            public void onDenied(@NonNull List<String> deniedForever, @NonNull List<String> denied) {
                ToastUtils.showShort(R.string.cam_no_camera_permission);
                finish();
            }
        }, Manifest.permission.CAMERA);
    }

    private void startCamera() {
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();
                cameraProvider.unbindAll();

                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                Size previewSize = calcPreviewSize(screenWidth, screenHeight);
                Size captureSize = calcCaptureSize();

                Preview.Builder previewBuilder = new Preview.Builder()
                        .setTargetRotation(currentSurfaceRotation);

                ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(currentSurfaceRotation);

                if (currentRatio == RATIO_1_1) {
                    previewBuilder.setTargetResolution(previewSize);
                    captureBuilder.setTargetResolution(captureSize);
                } else {
                    int aspectRatio = getTargetAspectRatio();
                    previewBuilder.setTargetAspectRatio(aspectRatio);
                    captureBuilder.setTargetAspectRatio(aspectRatio);
                }

                preview = previewBuilder.build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = captureBuilder.build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                applyFlashMode();

                adjustPreviewViewRatio(screenWidth, screenHeight);

            } catch (ExecutionException | InterruptedException e) {
                LogUtils.w("启动相机失败: " + e.getMessage(), e);
                ToastUtils.showShort(getString(R.string.cam_start_camera_failed, e.getMessage()));
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private int getTargetAspectRatio() {
        switch (currentRatio) {
            case RATIO_9_16:
            case RATIO_FULL:
                return AspectRatio.RATIO_16_9;
            default:
                return AspectRatio.RATIO_4_3;
        }
    }

    /**
     * Preview size: close to screen dimensions at the selected aspect ratio.
     */
    private Size calcPreviewSize(int screenW, int screenH) {
        int w, h;
        switch (currentRatio) {
            case RATIO_1_1:
                w = screenW;
                h = screenW;
                break;
            case RATIO_9_16:
                w = screenW;
                h = screenW * 16 / 9;
                break;
            case RATIO_FULL:
                w = screenW;
                h = screenH;
                break;
            default: // 3:4
                w = screenW;
                h = screenW * 4 / 3;
                break;
        }
        return new Size(Math.max(w, h), Math.min(w, h));
    }

    /**
     * Capture size: close to 12MP with the selected aspect ratio.
     * 12MP = 12,000,000 pixels.
     */
    private Size calcCaptureSize() {
        double totalPixels = 12_000_000.0;
        int w, h;
        switch (currentRatio) {
            case RATIO_1_1:
                w = (int) Math.sqrt(totalPixels);
                h = w;
                break;
            case RATIO_9_16:
                // w:h = 9:16, area = 9k*16k = 144k^2
                w = (int) (Math.sqrt(totalPixels * 9.0 / 16.0));
                h = w * 16 / 9;
                break;
            case RATIO_FULL: {
                int sw = getResources().getDisplayMetrics().widthPixels;
                int sh = getResources().getDisplayMetrics().heightPixels;
                double ratio = (double) sh / sw;
                w = (int) Math.sqrt(totalPixels / ratio);
                h = (int) (w * ratio);
                break;
            }
            default: // 3:4
                // w:h = 3:4, area = 3k*4k = 12k^2
                w = (int) (Math.sqrt(totalPixels * 3.0 / 4.0));
                h = w * 4 / 3;
                break;
        }
        return new Size(Math.max(w, h), Math.min(w, h));
    }

    /**
     * Adjust PreviewView layout to match the selected aspect ratio.
     */
    private void adjustPreviewViewRatio(int screenW, int screenH) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) previewView.getLayoutParams();
        switch (currentRatio) {
            case RATIO_1_1:
                lp.width = screenW;
                lp.height = screenW;
                break;
            case RATIO_3_4:
                lp.width = screenW;
                lp.height = screenW * 4 / 3;
                break;
            case RATIO_9_16:
                lp.width = screenW;
                lp.height = screenW * 16 / 9;
                break;
            default: // full
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT;
                lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
                break;
        }
        lp.gravity = Gravity.CENTER;
        previewView.setLayoutParams(lp);
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        setCaptureLoading(true);

        File photoFile = createFile();
        if (photoFile == null) {
            ToastUtils.showShort(R.string.cam_cannot_create_photo_file);
            setCaptureLoading(false);
            return;
        }

        ImageCapture.OutputFileOptions.Builder outputOptionsBuilder =
                new ImageCapture.OutputFileOptions.Builder(photoFile);
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            ImageCapture.Metadata metadata = new ImageCapture.Metadata();
            metadata.setReversedHorizontal(true);
            outputOptionsBuilder.setMetadata(metadata);
        }
        ImageCapture.OutputFileOptions options = outputOptionsBuilder.build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        lastPhotoPath = photoFile.getAbsolutePath();
                        normalizeAndShowPreview(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        setCaptureLoading(false);
                        LogUtils.w(exception);
                        ToastUtils.showShort(
                                getString(R.string.cam_capture_failed, exception.getMessage()));
                        CameraxCaptureUtil.notifyPhotoError(exception.getMessage());
                    }
                });
    }

    private void setCaptureLoading(boolean loading) {
        if (loading) {
            btnCapture.setEnabled(false);
            btnCapture.setVisibility(View.INVISIBLE);
            captureProgress.setVisibility(View.VISIBLE);
            btnSwitchCamera.setEnabled(false);
            btnClose.setEnabled(false);
        } else {
            captureProgress.setVisibility(View.GONE);
            btnCapture.setVisibility(View.VISIBLE);
            btnCapture.setEnabled(true);
            btnSwitchCamera.setEnabled(true);
            btnClose.setEnabled(true);
        }
    }

    private void normalizeAndShowPreview(File photoFile) {
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        cameraExecutor.execute(() -> {
            try {
                boolean frontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT;
                PhotoExifNormalizer.normalizeOrientationInPlace(photoFile, JPEG_QUALITY, frontCamera);
            } catch (Throwable e) {
                LogUtils.w("normalize photo orientation failed, use original file", e);
            }
            runOnUiThread(() -> {
                setCaptureLoading(false);
                showPreview(lastPhotoPath);
            });
        });
    }

    private void showPreview(String path) {
        previewOverlay.setVisibility(View.VISIBLE);
        Bitmap bitmap = loadBitmapWithExifOrientation(path);
        if (bitmap != null) {
            ivPreview.setImageBitmap(bitmap);
            applyOrientationDependentViews(currentSurfaceRotation);
        } else {
            ToastUtils.showShort(R.string.cam_cannot_load_preview);
        }
    }

    @Nullable
    private Bitmap loadBitmapWithExifOrientation(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            return null;
        }
        try {
            int rotation = new ExifInterface(path).getRotationDegrees();
            if (rotation == 0) {
                return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (Exception e) {
            LogUtils.w("read exif orientation failed: " + e.getMessage());
            return bitmap;
        }
    }

    private File createFile() {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DCIM), "CameraXPhoto");
        if (!dir.exists() && !dir.mkdirs()) return null;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "IMG_" + timestamp + ".jpg");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
        }
    }
}
