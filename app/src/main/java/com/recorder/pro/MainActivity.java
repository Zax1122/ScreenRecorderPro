package com.recorder.pro;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 100;
    private static final int REQ_MEDIA_PROJECTION = 200;
    private static final int REQ_OVERLAY = 300;

    private MediaProjectionManager projectionManager;
    private SharedPreferences prefs;

    private Button btnRecord, btnScreenshot, btnEditor, btnSettings;
    private TextView tvStatus, tvSavedPath;
    private Switch switchFacecam, switchMic, switchBubble;
    private LinearLayout llRecentFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("recorder_prefs", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        initViews();
        requestAllPermissions();
        refreshRecentFiles();
    }

    private void initViews() {
        btnRecord     = findViewById(R.id.btnRecord);
        btnScreenshot = findViewById(R.id.btnScreenshot);
        btnEditor     = findViewById(R.id.btnEditor);
        btnSettings   = findViewById(R.id.btnSettings);
        tvStatus      = findViewById(R.id.tvStatus);
        tvSavedPath   = findViewById(R.id.tvSavedPath);
        switchFacecam = findViewById(R.id.switchFacecam);
        switchMic     = findViewById(R.id.switchMic);
        switchBubble  = findViewById(R.id.switchBubble);
        llRecentFiles = findViewById(R.id.llRecentFiles);

        // Restore toggle states
        switchFacecam.setChecked(prefs.getBoolean("facecam", false));
        switchMic.setChecked(prefs.getBoolean("mic", true));
        switchBubble.setChecked(prefs.getBoolean("bubble", true));

        switchFacecam.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("facecam", c).apply());
        switchMic.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("mic", c).apply());
        switchBubble.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("bubble", c).apply());

        btnRecord.setOnClickListener(v -> startRecording());
        btnScreenshot.setOnClickListener(v -> takeScreenshot());
        btnEditor.setOnClickListener(v -> openEditor());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        String savePath = prefs.getString("save_path",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/ScreenRecorder");
        tvSavedPath.setText("Save to: " + savePath);
    }

    private void startRecording() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission();
            return;
        }
        tvStatus.setText("Requesting screen capture permission...");
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQ_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK) {
                // Launch floating bubble or start service directly
                boolean useBubble = switchBubble.isChecked();

                Intent serviceIntent = new Intent(this, ScreenRecordService.class);
                serviceIntent.putExtra("result_code", resultCode);
                serviceIntent.putExtra("result_data", data);
                serviceIntent.putExtra("use_facecam", switchFacecam.isChecked());
                serviceIntent.putExtra("use_mic", switchMic.isChecked());
                serviceIntent.putExtra("resolution", prefs.getString("resolution", "1080"));
                serviceIntent.putExtra("fps", prefs.getInt("fps", 60));
                serviceIntent.putExtra("bitrate", prefs.getInt("bitrate", 8));
                serviceIntent.putExtra("save_path", prefs.getString("save_path",
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/ScreenRecorder"));

                ContextCompat.startForegroundService(this, serviceIntent);

                if (useBubble) {
                    Intent bubbleIntent = new Intent(this, FloatingBubbleService.class);
                    startService(bubbleIntent);
                }

                tvStatus.setText("Recording started!");
                // Minimize app
                moveTaskToBack(true);
            } else {
                tvStatus.setText("Screen capture permission denied.");
            }
        } else if (requestCode == REQ_OVERLAY) {
            if (hasOverlayPermission()) startRecording();
        }
    }

    private void takeScreenshot() {
        if (!hasOverlayPermission()) { requestOverlayPermission(); return; }
        Intent intent = new Intent(this, ScreenRecordService.class);
        intent.setAction("ACTION_SCREENSHOT");
        startService(intent);
        tvStatus.setText("Screenshot saved!");
        Toast.makeText(this, "Screenshot taken!", Toast.LENGTH_SHORT).show();
    }

    private void openEditor() {
        startActivity(new Intent(this, VideoEditorActivity.class));
    }

    private void refreshRecentFiles() {
        llRecentFiles.removeAllViews();
        String savePath = prefs.getString("save_path",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/ScreenRecorder");
        File dir = new File(savePath);
        if (!dir.exists()) { dir.mkdirs(); return; }
        File[] files = dir.listFiles((d, name) ->
            name.endsWith(".mp4") || name.endsWith(".gif") || name.endsWith(".png"));
        if (files == null || files.length == 0) {
            TextView tv = new TextView(this);
            tv.setText("No recordings yet.");
            tv.setPadding(8, 4, 8, 4);
            llRecentFiles.addView(tv);
            return;
        }
        // Show last 5 files
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        int count = Math.min(files.length, 5);
        for (int i = 0; i < count; i++) {
            File f = files[i];
            Button btn = new Button(this);
            btn.setText(f.getName());
            btn.setTextSize(12f);
            final File ff = f;
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.fromFile(ff);
                String mime = ff.getName().endsWith(".gif") ? "image/gif" :
                              ff.getName().endsWith(".png") ? "image/png" : "video/mp4";
                intent.setDataAndType(uri, mime);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
            btn.setOnLongClickListener(v -> {
                // Long press: open in editor
                Intent edIntent = new Intent(this, VideoEditorActivity.class);
                edIntent.putExtra("video_path", ff.getAbsolutePath());
                startActivity(edIntent);
                return true;
            });
            llRecentFiles.addView(btn);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecentFiles();
        boolean recording = ScreenRecordService.isRecording;
        btnRecord.setText(recording ? "Stop Recording" : "Start Recording");
        tvStatus.setText(recording ? "● Recording in progress" : "Ready");
        if (recording) {
            btnRecord.setOnClickListener(v -> {
                Intent stop = new Intent(this, ScreenRecordService.class);
                stop.setAction("ACTION_STOP");
                startService(stop);
                btnRecord.setText("Start Recording");
                tvStatus.setText("Recording stopped.");
            });
        } else {
            btnRecord.setOnClickListener(v2 -> startRecording());
        }
    }

    // ── Permissions ──────────────────────────────────────────────

    private void requestAllPermissions() {
        String[] perms = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        };
        boolean needRequest = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true; break;
            }
        }
        if (needRequest) ActivityCompat.requestPermissions(this, perms, REQ_PERMISSIONS);
    }

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQ_OVERLAY);
    }
}
