package com.recorder.pro;

import android.content.SharedPreferences;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Spinner   spinResolution, spinFps;
    private SeekBar   sbBitrate;
    private TextView  tvBitrate;
    private EditText  etSavePath;
    private Button    btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("recorder_prefs", MODE_PRIVATE);

        spinResolution = findViewById(R.id.spinResolution);
        spinFps        = findViewById(R.id.spinFps);
        sbBitrate      = findViewById(R.id.sbBitrate);
        tvBitrate      = findViewById(R.id.tvBitrate);
        etSavePath     = findViewById(R.id.etSavePath);
        btnSave        = findViewById(R.id.btnSave);

        // Resolution
        String[] resOptions = {"1080p", "720p", "480p"};
        spinResolution.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, resOptions));
        String savedRes = prefs.getString("resolution", "1080");
        if ("720".equals(savedRes))  spinResolution.setSelection(1);
        else if ("480".equals(savedRes)) spinResolution.setSelection(2);
        else spinResolution.setSelection(0);

        // FPS
        String[] fpsOptions = {"60", "30", "24", "15"};
        spinFps.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, fpsOptions));
        int savedFps = prefs.getInt("fps", 60);
        if (savedFps == 30) spinFps.setSelection(1);
        else if (savedFps == 24) spinFps.setSelection(2);
        else if (savedFps == 15) spinFps.setSelection(3);
        else spinFps.setSelection(0);

        // Bitrate (1-20 Mbps)
        int savedBitrate = prefs.getInt("bitrate", 8);
        sbBitrate.setMax(19);
        sbBitrate.setProgress(savedBitrate - 1);
        tvBitrate.setText("Bitrate: " + savedBitrate + " Mbps");
        sbBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                tvBitrate.setText("Bitrate: " + (p + 1) + " Mbps");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        // Save path
        etSavePath.setText(prefs.getString("save_path",
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MOVIES) + "/ScreenRecorder"));

        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String res = spinResolution.getSelectedItemPosition() == 1 ? "720"
                   : spinResolution.getSelectedItemPosition() == 2 ? "480" : "1080";
        String fpsStr = (String) spinFps.getSelectedItem();
        int fps = Integer.parseInt(fpsStr);
        int bitrate = sbBitrate.getProgress() + 1;
        String path = etSavePath.getText().toString().trim();
        if (path.isEmpty()) path = android.os.Environment
            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
            + "/ScreenRecorder";

        prefs.edit()
            .putString("resolution", res)
            .putInt("fps", fps)
            .putInt("bitrate", bitrate)
            .putString("save_path", path)
            .apply();

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
