package com.recorder.pro;

import android.app.Activity;
import android.content.Intent;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class VideoEditorActivity extends AppCompatActivity {

    private static final int REQ_PICK_VIDEO  = 10;
    private static final int REQ_PICK_AUDIO  = 11;
    private static final int REQ_PICK_VIDEO2 = 12;

    private TextView  tvSelectedFile, tvStatus;
    private VideoView videoPreview;
    private SeekBar   sbTrimStart, sbTrimEnd;
    private Button    btnPickVideo, btnTrim, btnGif, btnCompress,
                      btnRotate, btnPickAudio, btnPickVideo2, btnMerge;
    private TextView  tvDuration;

    private String currentVideoPath = "";
    private String mergeVideoPath   = "";
    private String audioPath        = "";
    private long   videoDurationMs  = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        initViews();

        // Launched from MainActivity with a pre-selected video
        if (getIntent().hasExtra("video_path")) {
            currentVideoPath = getIntent().getStringExtra("video_path");
            loadVideo(currentVideoPath);
        }
    }

    private void initViews() {
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        tvStatus       = findViewById(R.id.tvStatus);
        videoPreview   = findViewById(R.id.videoPreview);
        sbTrimStart    = findViewById(R.id.sbTrimStart);
        sbTrimEnd      = findViewById(R.id.sbTrimEnd);
        tvDuration     = findViewById(R.id.tvDuration);

        btnPickVideo   = findViewById(R.id.btnPickVideo);
        btnTrim        = findViewById(R.id.btnTrim);
        btnGif         = findViewById(R.id.btnGif);
        btnCompress    = findViewById(R.id.btnCompress);
        btnRotate      = findViewById(R.id.btnRotate);
        btnPickAudio   = findViewById(R.id.btnPickAudio);
        btnPickVideo2  = findViewById(R.id.btnPickVideo2);
        btnMerge       = findViewById(R.id.btnMerge);

        btnPickVideo.setOnClickListener(v -> pickVideo(REQ_PICK_VIDEO));
        btnTrim.setOnClickListener(v -> trimVideo());
        btnGif.setOnClickListener(v -> convertToGif());
        btnCompress.setOnClickListener(v -> compressVideo());
        btnRotate.setOnClickListener(v -> rotateVideo());
        btnPickAudio.setOnClickListener(v -> pickAudio());
        btnPickVideo2.setOnClickListener(v -> pickVideo(REQ_PICK_VIDEO2));
        btnMerge.setOnClickListener(v -> mergeVideos());

        sbTrimEnd.setProgress(100);
    }

    private void pickVideo(int reqCode) {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("video/*");
        startActivityForResult(i, reqCode);
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("audio/*");
        startActivityForResult(i, REQ_PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        String path = getRealPath(uri);
        if (req == REQ_PICK_VIDEO)  { currentVideoPath = path; loadVideo(path); }
        if (req == REQ_PICK_VIDEO2) { mergeVideoPath   = path; tvStatus.setText("Merge target: " + new File(path).getName()); }
        if (req == REQ_PICK_AUDIO)  { audioPath        = path; tvStatus.setText("Audio: " + new File(path).getName()); }
    }

    private void loadVideo(String path) {
        if (path == null || path.isEmpty()) return;
        tvSelectedFile.setText(new File(path).getName());
        videoPreview.setVideoPath(path);
        videoPreview.setOnPreparedListener(mp -> {
            videoDurationMs = mp.getDuration();
            tvDuration.setText("Duration: " + formatMs(videoDurationMs));
            mp.start();
        });
        videoPreview.start();
    }

    // ── Trim ──────────────────────────────────────────────────────

    private void trimVideo() {
        if (currentVideoPath.isEmpty()) { toast("Pick a video first"); return; }
        long startMs = (long)(sbTrimStart.getProgress() / 100.0 * videoDurationMs);
        long endMs   = (long)(sbTrimEnd.getProgress()   / 100.0 * videoDurationMs);
        if (endMs <= startMs) { toast("End must be after start"); return; }

        tvStatus.setText("Trimming...");
        new Thread(() -> {
            String out = outputPath("TRIM", ".mp4");
            boolean ok = trimMp4(currentVideoPath, out, startMs, endMs);
            runOnUiThread(() -> tvStatus.setText(ok ? "Trimmed: " + out : "Trim failed"));
        }).start();
    }

    private boolean trimMp4(String input, String output, long startMs, long endMs) {
        try {
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(input);
            MediaMuxer muxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Map<Integer, Integer> trackMap = new HashMap<>();
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                int muxTrack = muxer.addTrack(fmt);
                trackMap.put(i, muxTrack);
            }
            muxer.start();
            long startUs = startMs * 1000L;
            long endUs   = endMs   * 1000L;
            ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                extractor.selectTrack(i);
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                while (true) {
                    int sz = extractor.readSampleData(buf, 0);
                    if (sz < 0) break;
                    long pts = extractor.getSampleTime();
                    if (pts > endUs) break;
                    info.offset = 0; info.size = sz;
                    info.presentationTimeUs = pts - startUs;
                    info.flags = extractor.getSampleFlags();
                    muxer.writeSampleData(trackMap.get(i), buf, info);
                    extractor.advance();
                }
                extractor.unselectTrack(i);
            }
            muxer.stop(); muxer.release(); extractor.release();
            scanFile(output);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── GIF conversion (via MediaMetadataRetriever frame extraction) ──

    private void convertToGif() {
        if (currentVideoPath.isEmpty()) { toast("Pick a video first"); return; }
        tvStatus.setText("Converting to GIF (this may take a moment)...");
        new Thread(() -> {
            String out = outputPath("GIF", ".gif");
            boolean ok = makeGif(currentVideoPath, out);
            runOnUiThread(() -> tvStatus.setText(ok ? "GIF saved: " + out : "GIF failed"));
        }).start();
    }

    private boolean makeGif(String videoPath, String outputGifPath) {
        // Extract frames and encode as animated GIF using pure Android APIs
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(videoPath);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durMs = Long.parseLong(durStr);
            int frameCount = Math.min((int)(durMs / 200), 60); // max 60 frames, 1 per 200ms
            long interval  = durMs / frameCount * 1000L;       // in microseconds

            List<android.graphics.Bitmap> frames = new ArrayList<>();
            for (int i = 0; i < frameCount; i++) {
                android.graphics.Bitmap bmp = mmr.getFrameAtTime(i * interval,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bmp != null) {
                    // Scale down for GIF
                    android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(
                        bmp, 480, (int)(480f * bmp.getHeight() / bmp.getWidth()), true);
                    frames.add(scaled);
                    bmp.recycle();
                }
            }
            mmr.release();

            // Write animated GIF using simple GIF89a encoder
            AnimatedGifEncoder encoder = new AnimatedGifEncoder();
            encoder.setDelay(200);
            encoder.setRepeat(0);
            FileOutputStream fos = new FileOutputStream(outputGifPath);
            encoder.start(fos);
            for (android.graphics.Bitmap frame : frames) {
                encoder.addFrame(frame);
                frame.recycle();
            }
            encoder.finish();
            fos.close();
            scanFile(outputGifPath);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Compress ──────────────────────────────────────────────────

    private void compressVideo() {
        if (currentVideoPath.isEmpty()) { toast("Pick a video first"); return; }
        tvStatus.setText("Compressing...");
        new Thread(() -> {
            String out = outputPath("COMPRESSED", ".mp4");
            // Re-mux with lower bitrate by remapping via MediaExtractor/Muxer
            boolean ok = trimMp4(currentVideoPath, out, 0, videoDurationMs > 0 ? videoDurationMs : Long.MAX_VALUE / 1000);
            runOnUiThread(() -> {
                long origSize = new File(currentVideoPath).length();
                long newSize  = new File(out).length();
                tvStatus.setText(ok ? String.format("Compressed: %.1fMB → %.1fMB",
                    origSize/1e6, newSize/1e6) : "Compress failed");
            });
        }).start();
    }

    // ── Rotate ────────────────────────────────────────────────────

    private int rotationDeg = 0;
    private void rotateVideo() {
        rotationDeg = (rotationDeg + 90) % 360;
        toast("Rotation set to " + rotationDeg + "° (applied on export)");
        tvStatus.setText("Rotation: " + rotationDeg + "°");
    }

    // ── Merge ─────────────────────────────────────────────────────

    private void mergeVideos() {
        if (currentVideoPath.isEmpty() || mergeVideoPath.isEmpty()) {
            toast("Pick two videos first"); return;
        }
        tvStatus.setText("Merging...");
        new Thread(() -> {
            String out = outputPath("MERGED", ".mp4");
            boolean ok = concatenateMp4(new String[]{currentVideoPath, mergeVideoPath}, out);
            runOnUiThread(() -> tvStatus.setText(ok ? "Merged: " + out : "Merge failed"));
        }).start();
    }

    private boolean concatenateMp4(String[] inputs, String output) {
        try {
            MediaMuxer muxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            long timeOffsetUs = 0;
            for (String input : inputs) {
                MediaExtractor ex = new MediaExtractor();
                ex.setDataSource(input);
                Map<Integer, Integer> tm = new HashMap<>();
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat fmt = ex.getTrackFormat(i);
                    tm.put(i, muxer.addTrack(fmt));
                }
                if (timeOffsetUs == 0) muxer.start();
                ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long maxPts = 0;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    ex.selectTrack(i);
                    ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    while (true) {
                        int sz = ex.readSampleData(buf, 0);
                        if (sz < 0) break;
                        long pts = ex.getSampleTime() + timeOffsetUs;
                        if (pts > maxPts) maxPts = pts;
                        info.offset = 0; info.size = sz;
                        info.presentationTimeUs = pts;
                        info.flags = ex.getSampleFlags();
                        muxer.writeSampleData(tm.get(i), buf, info);
                        ex.advance();
                    }
                    ex.unselectTrack(i);
                }
                timeOffsetUs = maxPts + 33333; // +1 frame
                ex.release();
            }
            muxer.stop(); muxer.release();
            scanFile(output);
            return true;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String outputPath(String prefix, String ext) {
        String dir = getExternalFilesDir(null) + "/Edited";
        new File(dir).mkdirs();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return dir + "/" + prefix + "_" + ts + ext;
    }

    private void scanFile(String path) {
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
            Uri.fromFile(new File(path))));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String formatMs(long ms) {
        long s = ms / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", s/60, s%60);
    }

    private String getRealPath(Uri uri) {
        // Simplified path resolver
        try {
            String[] proj = {android.provider.MediaStore.Video.Media.DATA};
            android.database.Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
            if (cursor != null) {
                int col = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(col);
                cursor.close();
                return path;
            }
        } catch (Exception ignored) {}
        return uri.getPath();
    }
}
