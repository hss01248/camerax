package com.hss01248.camerax;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.MirrorMode;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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

public class CameraxRecordVideoActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    public static final String EXTRA_MAX_DURATION_SECONDS = "extra_max_duration_seconds";
    public static final String EXTRA_PREFER_FRONT_CAMERA = "extra_prefer_front_camera";

    private static final int FLASH_OFF = 0;
    private static final int FLASH_ON = 1;
    private static final int FLASH_AUTO = 2;

    private PreviewView previewView;
    private ImageButton btnRecord;
    private ImageButton btnSwitchCamera;
    private ImageButton btnClose;
    private ImageButton btnFlash;
    private ImageButton btnPreviewCancel;
    private ImageButton btnPreviewConfirm;
    private ImageButton btnPlayOverlay;
    private SeekBar videoSeekBar;
    private TextView tvVideoCurrent;
    private TextView tvVideoDuration;
    private Chronometer chronometer;
    private LinearLayout timerBar;
    private LinearLayout topSection;
    private LinearLayout bottomBar;
    private LinearLayout previewControls;
    private LinearLayout previewBottomBar;
    private FrameLayout previewOverlay;
    private VideoView videoPreview;
    private Insets systemBarInsets = Insets.NONE;

    private Preview preview;
    private Camera camera;
    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private OrientationEventListener orientationEventListener;
    private int currentSurfaceRotation = Surface.ROTATION_0;
    private ExecutorService cameraExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable maxDurationRunnable;
    private Runnable progressUpdater;
    private boolean isSeekBarTracking = false;
    private int videoDurationMs = 0;

    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = FLASH_OFF;
    private int maxDurationSeconds;
    private boolean isRecording = false;
    private File currentVideoFile;
    private String lastVideoPath;

    private AudioManager audioManager;
    private AudioFocusRequest recordingFocusRequest;
    private AudioFocusRequest playbackFocusRequest;
    private boolean hasRecordingAudioFocus = false;
    private boolean hasPlaybackAudioFocus = false;
    private boolean isVideoUriSet = false;

    public static void start(Context context) {
        start(context, 0);
    }

    public static void start(Context context, int maxDurationSeconds) {
        start(context, maxDurationSeconds, false);
    }

    public static void start(Context context, int maxDurationSeconds, boolean preferFrontCamera) {
        Intent intent = new Intent(context, CameraxRecordVideoActivity.class);
        if (maxDurationSeconds > 0) {
            intent.putExtra(EXTRA_MAX_DURATION_SECONDS, maxDurationSeconds);
        }
        intent.putExtra(EXTRA_PREFER_FRONT_CAMERA, preferFrontCamera);
        context.startActivity(intent);
    }

    public static void startForResult(Activity activity, int requestCode) {
        startForResult(activity, requestCode, 0);
    }

    public static void startForResult(Activity activity, int requestCode, int maxDurationSeconds) {
        startForResult(activity, requestCode, maxDurationSeconds, false);
    }

    public static void startForResult(Activity activity, int requestCode, int maxDurationSeconds,
                                      boolean preferFrontCamera) {
        Intent intent = new Intent(activity, CameraxRecordVideoActivity.class);
        if (maxDurationSeconds > 0) {
            intent.putExtra(EXTRA_MAX_DURATION_SECONDS, maxDurationSeconds);
        }
        intent.putExtra(EXTRA_PREFER_FRONT_CAMERA, preferFrontCamera);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camerax_record_video);

        maxDurationSeconds = Math.max(0, getIntent().getIntExtra(EXTRA_MAX_DURATION_SECONDS, 0));
        applyInitialLensFacing();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);
            insetsController.setAppearanceLightNavigationBars(false);
        }

        previewView = findViewById(R.id.previewView);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        btnRecord = findViewById(R.id.btnRecord);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnClose = findViewById(R.id.btnClose);
        btnFlash = findViewById(R.id.btnFlash);
        chronometer = findViewById(R.id.chronometer);
        timerBar = findViewById(R.id.timerBar);
        topSection = findViewById(R.id.topSection);
        bottomBar = findViewById(R.id.bottomBar);
        previewOverlay = findViewById(R.id.previewOverlay);
        previewControls = findViewById(R.id.previewControls);
        previewBottomBar = findViewById(R.id.previewBottomBar);
        videoPreview = findViewById(R.id.videoPreview);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            videoPreview.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        }
        btnPlayOverlay = findViewById(R.id.btnPlayOverlay);
        videoSeekBar = findViewById(R.id.videoSeekBar);
        tvVideoCurrent = findViewById(R.id.tvVideoCurrent);
        tvVideoDuration = findViewById(R.id.tvVideoDuration);
        btnPreviewCancel = findViewById(R.id.btnPreviewCancel);
        btnPreviewConfirm = findViewById(R.id.btnPreviewConfirm);
        setupWindowInsets();
        setupVideoSeekBar();
        initOrientationListener();

        btnRecord.setVisibility(View.GONE);
        btnSwitchCamera.setVisibility(View.GONE);
        updateFlashIcon();

        btnClose.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            }
            releaseVideoPreview();
            CameraxCaptureUtil.notifyVideoCancel();
            setResult(RESULT_CANCELED);
            finish();
        });

        btnSwitchCamera.setOnClickListener(v -> {
            if (isRecording || isPreviewVisible()) return;
            lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                    ? CameraSelector.LENS_FACING_FRONT
                    : CameraSelector.LENS_FACING_BACK;
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                flashMode = FLASH_OFF;
                updateFlashIcon();
            }
            previewView.post(this::startCamera);
        });

        btnFlash.setOnClickListener(v -> {
            if (isRecording || isPreviewVisible()) return;
            flashMode = (flashMode + 1) % 3;
            updateFlashIcon();
            applyFlashMode();
        });

        btnRecord.setOnClickListener(v -> {
            if (isPreviewVisible()) return;
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        btnPreviewCancel.setOnClickListener(v -> {
            if (lastVideoPath != null) {
                new File(lastVideoPath).delete();
                lastVideoPath = null;
            }
            releaseVideoPreview();
            previewOverlay.setVisibility(View.GONE);
            btnSwitchCamera.setVisibility(View.VISIBLE);
            btnClose.setVisibility(View.VISIBLE);
            applyFlashMode();
            startCamera();
        });

        btnPreviewConfirm.setOnClickListener(v -> {
            if (lastVideoPath != null) {
                releaseVideoPreview();
                CameraxCaptureUtil.notifyVideoSuccess(lastVideoPath);
                Intent data = new Intent();
                data.putExtra(EXTRA_VIDEO_PATH, lastVideoPath);
                setResult(RESULT_OK, data);
                finish();
            }
        });

        View.OnClickListener playClickListener = v -> toggleVideoPlayback();
        btnPlayOverlay.setOnClickListener(playClickListener);
        videoPreview.setOnClickListener(playClickListener);
        videoPreview.setOnCompletionListener(mp -> {
            btnPlayOverlay.setVisibility(View.VISIBLE);
            stopProgressUpdater();
            abandonPlaybackAudioFocus();
            if (videoDurationMs > 0) {
                videoSeekBar.setProgress(videoDurationMs);
                tvVideoCurrent.setText(formatDuration(videoDurationMs));
            }
        });

        requestPermissionAndStart();
    }

    private void applyInitialLensFacing() {
        if (getIntent().getBooleanExtra(EXTRA_PREFER_FRONT_CAMERA, false)) {
            lensFacing = CameraSelector.LENS_FACING_FRONT;
            flashMode = FLASH_OFF;
        }
    }

    private boolean isPreviewVisible() {
        return previewOverlay != null && previewOverlay.getVisibility() == View.VISIBLE;
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(topSection, (v, insets) -> {
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            topSection.setPadding(0, systemBarInsets.top, 0, 0);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(previewControls, (v, insets) -> {
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            return insets;
        });
        ViewCompat.requestApplyInsets(topSection);
    }

    private void setupVideoSeekBar() {
        videoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvVideoCurrent.setText(formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarTracking = false;
                if (videoPreview != null && videoDurationMs > 0) {
                    videoPreview.seekTo(seekBar.getProgress());
                }
            }
        });
    }


    private String formatDuration(int millis) {
        int totalSeconds = Math.max(0, millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int dp2px(int dp) {
        return CameraxUtil.dp2px(this, dp);
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
        return CameraxUtil.orientationToSurfaceRotation(orientation);
    }

    private void applyTargetRotation(int rotation) {
        if (currentSurfaceRotation == rotation) {
            return;
        }
        currentSurfaceRotation = rotation;
        if (preview != null) {
            preview.setTargetRotation(rotation);
        }
        // 录制开始后不再改变 videoCapture 的 target rotation，锁定录制时的方向
        if (!isRecording && videoCapture != null) {
            videoCapture.setTargetRotation(rotation);
        }
        applyOrientationDependentViews(rotation);
    }

    /** 按钮位置不动，仅旋转图标 */
    private void applyOrientationDependentViews(int surfaceRotation) {
        float degrees = surfaceRotationToDegrees(surfaceRotation);
        rotateViewForOrientation(btnClose, degrees);
        rotateViewForOrientation(btnFlash, degrees);
        rotateViewForOrientation(btnSwitchCamera, degrees);
        rotateViewForOrientation(btnPreviewCancel, degrees);
        rotateViewForOrientation(btnPreviewConfirm, degrees);
    }

    private static void rotateViewForOrientation(View view, float degrees) {
        CameraxUtil.rotateViewForOrientation(view, degrees);
    }

    private static float surfaceRotationToDegrees(int surfaceRotation) {
        return CameraxUtil.surfaceRotationToDegrees(surfaceRotation);
    }

    private void updateFlashIcon() {
        switch (flashMode) {
            case FLASH_ON:
                btnFlash.setImageResource(R.drawable.ic_cam_flash_on);
                btnFlash.setContentDescription(getString(R.string.cam_cd_flash_on));
                break;
            case FLASH_AUTO:
                btnFlash.setImageResource(R.drawable.ic_cam_flash_auto);
                btnFlash.setContentDescription(getString(R.string.cam_cd_flash_auto));
                break;
            default:
                btnFlash.setImageResource(R.drawable.ic_cam_flash_off);
                btnFlash.setContentDescription(getString(R.string.cam_cd_flash_off));
                break;
        }
    }

    private void applyFlashMode() {
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            btnFlash.setVisibility(View.GONE);
            flashMode = FLASH_OFF;
            updateFlashIcon();
            if (camera != null) {
                camera.getCameraControl().enableTorch(false);
            }
            return;
        }

        btnFlash.setVisibility(View.VISIBLE);
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

    private void requestPermissionAndStart() {
        MyPermissions.requestByMostEffort(false, true, new PermissionUtils.FullCallback() {
            @Override
            public void onGranted(@NonNull List<String> granted) {
                btnRecord.setVisibility(View.VISIBLE);
                btnSwitchCamera.setVisibility(View.VISIBLE);
                previewView.post(CameraxRecordVideoActivity.this::startCamera);
            }

            @Override
            public void onDenied(@NonNull List<String> deniedForever, @NonNull List<String> denied) {
                ToastUtils.showShort(R.string.cam_no_camera_mic_permission);
                finish();
            }
        }, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);
    }

    @SuppressWarnings("MissingPermission")
    private void startCamera() {
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                int screenW = previewView.getWidth();
                if (screenW <= 0) {
                    screenW = getResources().getDisplayMetrics().widthPixels;
                }
                adjustPreviewViewRatio(screenW);

                Size previewTargetSize = resolvePreviewTargetSize(screenW);
                int maxDim = Math.max(previewTargetSize.getWidth(), previewTargetSize.getHeight());
                int minDim = Math.min(previewTargetSize.getWidth(), previewTargetSize.getHeight());
                Quality videoQuality = resolveVideoQuality(maxDim, minDim);

                ResolutionSelector previewResSelector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(new ResolutionStrategy(
                                previewTargetSize,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();

                preview = new Preview.Builder()
                        .setResolutionSelector(previewResSelector)
                        .setTargetRotation(currentSurfaceRotation)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(videoQuality,
                                FallbackStrategy.higherQualityOrLowerThan(videoQuality)))
                        .setExecutor(cameraExecutor)
                        .build();
                videoCapture = new VideoCapture.Builder<>(recorder)
                        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                        .build();
                videoCapture.setTargetRotation(currentSurfaceRotation);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
                applyFlashMode();

            } catch (ExecutionException | InterruptedException e) {
                LogUtils.w("start camera failed: " + e.getMessage(), e);
                ToastUtils.showShort(getString(R.string.cam_start_camera_failed, e.getMessage()));
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void adjustPreviewViewRatio(int screenW) {
        applyPreviewFrameLayout(previewView, screenW);
    }

    private void applyPreviewFrameLayout(View view, int screenW) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = screenW;
        lp.height = screenW * 16 / 9;
        lp.gravity = Gravity.CENTER;
        view.setLayoutParams(lp);
    }

    private Size resolvePreviewTargetSize(int screenW) {
        int previewH = screenW * 16 / 9;
        return new Size(Math.max(screenW, previewH), Math.min(screenW, previewH));
    }

    private Quality resolveVideoQuality(int maxDim, int minDim) {
        if (maxDim >= 2160) {
            return Quality.UHD;
        }
        if (maxDim >= 1080) {
            return Quality.FHD;
        }
        if (maxDim >= 720) {
            return Quality.HD;
        }
        return Quality.SD;
    }

    @SuppressWarnings("MissingPermission")
    private void startRecording() {
        if (videoCapture == null) return;

        requestRecordingAudioFocus();

        currentVideoFile = createVideoFile();
        if (currentVideoFile == null) {
            ToastUtils.showShort(R.string.cam_cannot_create_video_file);
            abandonRecordingAudioFocus();
            return;
        }

        FileOutputOptions outputOptions = new FileOutputOptions.Builder(currentVideoFile).build();

        activeRecording = videoCapture.getOutput()
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        onRecordingStarted();
                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) videoRecordEvent;
                        onRecordingFinalized(finalizeEvent);
                    }
                });
    }

    private void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        abandonRecordingAudioFocus();
    }

    private void onRecordingStarted() {
        isRecording = true;
        btnSwitchCamera.setVisibility(View.GONE);
        btnFlash.setVisibility(View.GONE);
        btnClose.setVisibility(View.GONE);
        timerBar.setVisibility(View.VISIBLE);
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
        btnRecord.setImageResource(R.drawable.ic_cam_record_active);

        if (maxDurationSeconds > 0) {
            maxDurationRunnable = () -> {
                if (isRecording) {
                    stopRecording();
                }
            };
            mainHandler.postDelayed(maxDurationRunnable, maxDurationSeconds * 1000L);
        }
    }

    private void onRecordingFinalized(VideoRecordEvent.Finalize event) {
        clearMaxDurationCallback();
        isRecording = false;
        chronometer.stop();
        timerBar.setVisibility(View.GONE);
        btnRecord.setImageResource(R.drawable.ic_cam_record_idle);

        if (!event.hasError()) {
            lastVideoPath = currentVideoFile.getAbsolutePath();
            showVideoPreview(lastVideoPath);
        } else {
            btnSwitchCamera.setVisibility(View.VISIBLE);
            btnClose.setVisibility(View.VISIBLE);
            applyFlashMode();
            LogUtils.w("record failed, error code: " + event.getError());
            String cause = event.getCause() != null ? event.getCause().getMessage() : "";
            ToastUtils.showShort(getString(R.string.cam_video_record_failed, cause));
            CameraxCaptureUtil.notifyVideoError(getString(R.string.cam_video_record_failed, cause));
        }
    }

    private void clearMaxDurationCallback() {
        if (maxDurationRunnable != null) {
            mainHandler.removeCallbacks(maxDurationRunnable);
            maxDurationRunnable = null;
        }
    }

    private void showVideoPreview(String path) {
        previewOverlay.setVisibility(View.VISIBLE);
        btnPlayOverlay.setVisibility(View.VISIBLE);
        resetVideoProgressUi();

        try {
            ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
            cameraProvider.unbindAll();
        } catch (Exception e) {
            LogUtils.w("unbind camera failed: " + e.getMessage());
        }

        int screenW = previewView.getWidth();
        if (screenW <= 0) {
            screenW = getResources().getDisplayMetrics().widthPixels;
        }
        int screenH = previewView.getHeight();
        if (screenH <= 0) {
            screenH = getResources().getDisplayMetrics().heightPixels;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);

            int videoW = parseIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            int videoH = parseIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int rotation = parseIntMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

            if (rotation == 90 || rotation == 270) {
                int tmp = videoW; videoW = videoH; videoH = tmp;
            }

            if (videoW > 0 && videoH > 0) {
                float videoAspect = (float) videoW / videoH;
                float screenAspect = (float) screenW / screenH;
                int layoutW, layoutH;
                if (videoAspect > screenAspect) {
                    layoutW = screenW;
                    layoutH = Math.round(screenW / videoAspect);
                } else {
                    layoutH = screenH;
                    layoutW = Math.round(screenH * videoAspect);
                }
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoPreview.getLayoutParams();
                lp.width = layoutW;
                lp.height = layoutH;
                lp.gravity = Gravity.CENTER;
                videoPreview.setLayoutParams(lp);
            } else {
                applyPreviewFrameLayout(videoPreview, screenW);
            }

            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                videoDurationMs = Integer.parseInt(durationStr);
                videoSeekBar.setMax(Math.max(videoDurationMs, 1));
                tvVideoDuration.setText(formatDuration(videoDurationMs));
                tvVideoCurrent.setText(formatDuration(0));
                videoSeekBar.setProgress(0);
            }
        } catch (Exception e) {
            LogUtils.w("showVideoPreview failed: " + e.getMessage());
            applyPreviewFrameLayout(videoPreview, screenW);
        } finally {
            try { retriever.release(); } catch (Exception e) {
                LogUtils.w("MediaMetadataRetriever release failed: " + e.getMessage());
            }
        }

        videoPreview.setVideoURI(Uri.fromFile(new File(path)));
        videoPreview.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            videoPreview.seekTo(0);
            if (videoDurationMs <= 0) {
                videoDurationMs = mp.getDuration();
                videoSeekBar.setMax(Math.max(videoDurationMs, 1));
                tvVideoDuration.setText(formatDuration(videoDurationMs));
            }
            isVideoUriSet = true;
        });
        videoPreview.setOnErrorListener((mp, what, extra) -> {
            LogUtils.w("VideoView prepare error: what=" + what + " extra=" + extra);
            return true;
        });
    }

    private static int parseIntMetadata(MediaMetadataRetriever retriever, int key) {
        String value = retriever.extractMetadata(key);
        if (value != null) {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private void loadVideoAndPlay() {
        if (!isVideoUriSet) {
            videoPreview.setVideoURI(Uri.fromFile(new File(lastVideoPath)));
            videoPreview.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                if (videoDurationMs <= 0) {
                    videoDurationMs = mp.getDuration();
                    videoSeekBar.setMax(Math.max(videoDurationMs, 1));
                    tvVideoDuration.setText(formatDuration(videoDurationMs));
                }
                isVideoUriSet = true;
                videoPreview.start();
                btnPlayOverlay.setVisibility(View.GONE);
                startProgressUpdater();
            });
            videoPreview.setOnErrorListener((mp, what, extra) -> {
                ToastUtils.showShort(R.string.cam_cannot_load_video_preview);
                return true;
            });
        } else {
            videoPreview.seekTo(0);
            videoSeekBar.setProgress(0);
            tvVideoCurrent.setText(formatDuration(0));
            videoPreview.start();
            btnPlayOverlay.setVisibility(View.GONE);
            startProgressUpdater();
        }
    }

    private void resetVideoProgressUi() {
        stopProgressUpdater();
        videoDurationMs = 0;
        videoSeekBar.setProgress(0);
        videoSeekBar.setMax(1000);
        tvVideoCurrent.setText(formatDuration(0));
        tvVideoDuration.setText(formatDuration(0));
    }

    private void startProgressUpdater() {
        stopProgressUpdater();
        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (videoPreview != null && videoPreview.isPlaying() && !isSeekBarTracking) {
                    int position = videoPreview.getCurrentPosition();
                    videoSeekBar.setProgress(position);
                    tvVideoCurrent.setText(formatDuration(position));
                    mainHandler.postDelayed(this, 200);
                }
            }
        };
        mainHandler.post(progressUpdater);
    }

    private void stopProgressUpdater() {
        if (progressUpdater != null) {
            mainHandler.removeCallbacks(progressUpdater);
            progressUpdater = null;
        }
    }

    private void toggleVideoPlayback() {
        if (lastVideoPath == null) return;
        if (videoPreview.isPlaying()) {
            videoPreview.pause();
            btnPlayOverlay.setVisibility(View.VISIBLE);
            stopProgressUpdater();
            abandonPlaybackAudioFocus();
        } else {
            requestPlaybackAudioFocus();
            if (!isVideoUriSet) {
                loadVideoAndPlay();
            } else {
                videoPreview.start();
                btnPlayOverlay.setVisibility(View.GONE);
                startProgressUpdater();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void releaseVideoPreview() {
        stopProgressUpdater();
        if (videoPreview != null) {
            videoPreview.stopPlayback();
        }
        isVideoUriSet = false;
        resetVideoProgressUi();
        abandonPlaybackAudioFocus();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && audioManager != null) {
            audioManager.abandonAudioFocus(null);
        }
    }

    private void initAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean requestRecordingAudioFocus() {
        initAudioManager();
        if (hasRecordingAudioFocus) return true;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            recordingFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            mainHandler.post(() -> {
                                if (isRecording) {
                                    stopRecording();
                                }
                            });
                        }
                    })
                    .build();
            result = audioManager.requestAudioFocus(recordingFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    recordingFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
        }
        hasRecordingAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        return hasRecordingAudioFocus;
    }

    private final AudioManager.OnAudioFocusChangeListener recordingFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            mainHandler.post(() -> {
                if (isRecording) {
                    stopRecording();
                }
            });
        }
    };

    @SuppressWarnings("deprecation")
    private void abandonRecordingAudioFocus() {
        if (!hasRecordingAudioFocus || audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && recordingFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(recordingFocusRequest);
        } else {
            audioManager.abandonAudioFocus(recordingFocusChangeListener);
        }
        hasRecordingAudioFocus = false;
    }

    @SuppressWarnings("deprecation")
    private boolean requestPlaybackAudioFocus() {
        initAudioManager();
        if (hasPlaybackAudioFocus) return true;
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            playbackFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            mainHandler.post(() -> {
                                if (videoPreview != null && videoPreview.isPlaying()) {
                                    videoPreview.pause();
                                    btnPlayOverlay.setVisibility(View.VISIBLE);
                                    stopProgressUpdater();
                                }
                            });
                        }
                    })
                    .build();
            result = audioManager.requestAudioFocus(playbackFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    playbackFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        hasPlaybackAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        return hasPlaybackAudioFocus;
    }

    private final AudioManager.OnAudioFocusChangeListener playbackFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            mainHandler.post(() -> {
                if (videoPreview != null && videoPreview.isPlaying()) {
                    videoPreview.pause();
                    btnPlayOverlay.setVisibility(View.VISIBLE);
                    stopProgressUpdater();
                }
            });
        }
    };

    @SuppressWarnings("deprecation")
    private void abandonPlaybackAudioFocus() {
        if (!hasPlaybackAudioFocus || audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(playbackFocusRequest);
        } else {
            audioManager.abandonAudioFocus(playbackFocusChangeListener);
        }
        hasPlaybackAudioFocus = false;
    }

    private File createVideoFile() {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "CameraXVideo");
        if (!dir.exists() && !dir.mkdirs()) return null;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "VID_" + timestamp + ".mp4");
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

    @Override
    protected void onPause() {
        super.onPause();
        if (videoPreview != null && videoPreview.isPlaying()) {
            videoPreview.pause();
            btnPlayOverlay.setVisibility(View.VISIBLE);
            stopProgressUpdater();
            abandonPlaybackAudioFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (isRecording) {
            stopRecording();
            return;
        }
        if (isPreviewVisible()) {
            if (lastVideoPath != null) {
                new File(lastVideoPath).delete();
                lastVideoPath = null;
            }
            releaseVideoPreview();
            previewOverlay.setVisibility(View.GONE);
            btnSwitchCamera.setVisibility(View.VISIBLE);
            btnClose.setVisibility(View.VISIBLE);
            applyFlashMode();
            startCamera();
            return;
        }
        CameraxCaptureUtil.notifyVideoCancel();
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraxCaptureUtil.pendingVideoCallback = null;
        clearMaxDurationCallback();
        if (activeRecording != null) {
            activeRecording.stop();
        }
        abandonRecordingAudioFocus();
        releaseVideoPreview();
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
        }
    }
}
