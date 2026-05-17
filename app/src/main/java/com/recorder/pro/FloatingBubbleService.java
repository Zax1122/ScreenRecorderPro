package com.recorder.pro;

import android.app.Service;
import android.content.Intent;
import android.graphics.*;
import android.os.IBinder;
import android.view.*;
import android.view.WindowManager.LayoutParams;
import android.widget.*;

public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private View          bubbleView;
    private LayoutParams  params;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createBubble();
    }

    private void createBubble() {
        // Build the bubble layout programmatically
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setBackgroundResource(android.R.drawable.dialog_holo_dark_frame);
        layout.setPadding(8, 8, 8, 8);

        Button btnStop    = new Button(this);
        Button btnPause   = new Button(this);
        Button btnShot    = new Button(this);

        btnStop.setText("■");
        btnPause.setText("⏸");
        btnShot.setText("📷");

        btnStop.setTextSize(18f);
        btnPause.setTextSize(18f);
        btnShot.setTextSize(18f);

        int btnSize = 100;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
        lp.setMargins(4, 0, 4, 0);

        layout.addView(btnStop,  lp);
        layout.addView(btnPause, lp);
        layout.addView(btnShot,  lp);

        bubbleView = layout;

        int overlayType = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
            ? LayoutParams.TYPE_APPLICATION_OVERLAY
            : LayoutParams.TYPE_PHONE;

        params = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            overlayType,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        windowManager.addView(bubbleView, params);

        // Touch to drag
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            boolean moved = false;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        moved = false;
                        initialX = params.x; initialY = params.y;
                        initialTouchX = e.getRawX(); initialTouchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - initialTouchX);
                        int dy = (int)(e.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true;
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(bubbleView, params);
                        return true;
                }
                return false;
            }
        });

        // Button actions
        btnStop.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenRecordService.class);
            i.setAction("ACTION_STOP");
            startService(i);
            stopSelf();
        });

        btnPause.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenRecordService.class);
            i.setAction("ACTION_PAUSE");
            startService(i);
            btnPause.setText(ScreenRecordService.isPaused ? "▶" : "⏸");
        });

        btnShot.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenRecordService.class);
            i.setAction("ACTION_SCREENSHOT");
            startService(i);
            Toast.makeText(this, "Screenshot saved!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleView != null) windowManager.removeView(bubbleView);
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
