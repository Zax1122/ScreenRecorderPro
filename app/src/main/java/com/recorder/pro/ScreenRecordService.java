package com.recorder.pro;

import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class ScreenRecordService extends Service implements SensorEventListener {

    public static boolean isRecording = false;
    public static boolean isPaused    = false;

    private static final String CHANNEL_ID   = "RecorderChannel";
    private static final String CHANNEL_NAME = "Screen Recorder";
    private static final int    NOTIF_ID     = 1;

    private MediaProjection   mediaProjection;
    private MediaRecorder     mediaRecorder;
    private VirtualDisplay    virtualDisplay;
    private SensorManager     sensorManager;
    private Sensor            accelerometer;

    private String  outputPath;
    private String  saveDirPath;
    private int     width, height, fps, bitrateMbps;
    private boolean useMic, useFacecam;

    // Shake detection
    private long    lastShakeTime = 0;
    private float   lastX, lastY, lastZ;
    private boolean firstSensor = true;
    private static final float SHAKE_THRESHOLD = 18f;
    private static final long  SHAKE_COOLDOWN  = 2000;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        sensorManager  = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if ("ACTION_STOP".equals(action)) {
            stopRecording(); return START_NOT_STICKY;
        }
        if ("ACTION_PAUSE".equals(action)) {
            togglePause(); return START_NOT_STICKY;
        }
        if ("ACTION_SCREENSHOT".equals(action)) {
            // Screenshot handled via MediaProjection if available, else skip
            return START_NOT_STICKY;
        }

        // Start recording
        int resultCode = intent.getIntExtra("result_code", -1);
        Intent resultData = intent.getParcelableExtra("result_data");
        useMic       = intent.getBooleanExtra("use_mic", true);
        useFacecam   = intent.getBooleanExtra("use_facecam", false);
        saveDirPath  = intent.getStringExtra("save_path");
        fps          = intent.getIntExtra("fps", 60);
        bitrateMbps  = intent.getIntExtra("bitrate", 8);

        String res = intent.getStringExtra("resolution");
        if ("720".equals(res)) { width = 1280; height = 720; }
        else if ("480".equals(res)) { width = 854; height = 480; }
        else { width = 1920; height = 1080; }

        MediaProjectionManager mpm =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, resultData);

        startForeground(NOTIF_ID, buildNotification("Recording..."));
        beginRecording();

        // Register shake sensor
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        return START_NOT_STICKY;
    }

    // ── Recording Core ───────────────────────────────────────────

    private void beginRecording() {
        File dir = new File(saveDirPath);
        if (!dir.exists()) dir.mkdirs();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        outputPath = saveDirPath + "/REC_" + ts + ".mp4";

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int dpi = dm.densityDpi;

        mediaRecorder = new MediaRecorder();
        if (useMic) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (useMic) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(192000);
            mediaRecorder.setAudioSamplingRate(44100);
        }
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(fps);
        mediaRecorder.setVideoEncodingBitRate(bitrateMbps * 1_000_000);
        mediaRecorder.setOutputFile(outputPath);

        try {
            mediaRecorder.prepare();
        } catch (Exception e) { e.printStackTrace(); stopSelf(); return; }

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecorder", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.getSurface(), null, null);

        mediaRecorder.start();
        isRecording = true;
        isPaused    = false;

        updateNotification("● Recording — tap to open app");
    }

    private void stopRecording() {
        sensorManager.unregisterListener(this);
        if (mediaRecorder != null) {
            try {
                if (isPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    mediaRecorder.resume();
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (mediaProjection  != null) { mediaProjection.stop();  mediaProjection  = null; }
        isRecording = false;
        isPaused    = false;

        // Notify media scanner
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
            android.net.Uri.fromFile(new File(outputPath))));

        stopForeground(true);
        stopSelf();
    }

    private void togglePause() {
        if (!isRecording || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (isPaused) {
            mediaRecorder.resume();
            isPaused = false;
            updateNotification("● Recording — tap to open app");
        } else {
            mediaRecorder.pause();
            isPaused = true;
            updateNotification("⏸ Paused — tap to open app");
        }
    }

    // ── Shake-to-Stop ────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        if (firstSensor) { lastX = x; lastY = y; lastZ = z; firstSensor = false; return; }
        float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
        if (delta > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN) {
                lastShakeTime = now;
                stopRecording();
            }
        }
        lastX = x; lastY = y; lastZ = z;
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ── Notifications ─────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Screen recorder controls");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, ScreenRecordService.class);
        stopIntent.setAction("ACTION_STOP");
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(this, ScreenRecordService.class);
        pauseIntent.setAction("ACTION_PAUSE");
        PendingIntent pausePi = PendingIntent.getService(this, 2, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder Pro")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, isPaused ? "Resume" : "Pause", pausePi)
            .addAction(android.R.drawable.ic_media_ff, "Stop", stopPi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isRecording) stopRecording();
    }
}
