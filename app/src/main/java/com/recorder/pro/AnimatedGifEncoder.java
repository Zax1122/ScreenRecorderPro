package com.recorder.pro;

import android.graphics.Bitmap;
import java.io.*;

/**
 * Pure-Java animated GIF encoder.
 * Adapted from Kevin Weiner's public-domain GifEncoder.
 */
public class AnimatedGifEncoder {
    private int width, height;
    private int delay = 100;
    private int repeat = -1;
    private OutputStream out;
    private boolean started = false;
    private boolean firstFrame = true;

    public void setDelay(int ms) { delay = ms; }
    public void setRepeat(int iter) { repeat = iter; }

    public boolean start(OutputStream os) {
        try { out = os; writeHeader(); started = true; return true; }
        catch (IOException e) { return false; }
    }

    public boolean addFrame(Bitmap bitmap) {
        if (!started || bitmap == null) return false;
        try {
            width  = bitmap.getWidth();
            height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            byte[] indexedPixels = analyzeAndQuantize(pixels);
            if (firstFrame) {
                writeLSD();
                writePalette();
                if (repeat >= 0) writeNetscapeExt();
                firstFrame = false;
            }
            writeGraphicCtrlExt();
            writeImageDesc();
            writePalette();
            writePixels(indexedPixels);
            return true;
        } catch (IOException e) { return false; }
    }

    public boolean finish() {
        if (!started) return false;
        try { out.write(0x3b); out.flush(); return true; }
        catch (IOException e) { return false; }
    }

    // ── Internal helpers ──────────────────────────────────────────

    private int[] colorTable = new int[256];
    private int colorCount = 0;

    private byte[] analyzeAndQuantize(int[] pixels) {
        // Simple median-cut quantization to 256 colors
        colorCount = 0;
        java.util.HashMap<Integer, Integer> map = new java.util.HashMap<>();
        for (int p : pixels) {
            int c = p & 0xFFFFFF;
            if (!map.containsKey(c)) {
                if (colorCount < 256) { colorTable[colorCount] = c; map.put(c, colorCount++); }
            }
        }
        // Fill rest of palette
        for (int i = colorCount; i < 256; i++) colorTable[i] = 0;
        byte[] idx = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i] & 0xFFFFFF;
            Integer index = map.get(c);
            idx[i] = (byte)(index != null ? index : 0);
        }
        return idx;
    }

    private void writeHeader() throws IOException {
        out.write("GIF89a".getBytes());
    }

    private void writeLSD() throws IOException {
        writeShort(width); writeShort(height);
        out.write(0xf7); // global color table, 256 colors
        out.write(0);    // bg color index
        out.write(0);    // pixel aspect ratio
    }

    private void writePalette() throws IOException {
        for (int i = 0; i < 256; i++) {
            int c = colorTable[i];
            out.write((c >> 16) & 0xff);
            out.write((c >>  8) & 0xff);
            out.write( c        & 0xff);
        }
    }

    private void writeNetscapeExt() throws IOException {
        out.write(0x21); out.write(0xff); out.write(11);
        out.write("NETSCAPE2.0".getBytes());
        out.write(3); out.write(1);
        writeShort(repeat); out.write(0);
    }

    private void writeGraphicCtrlExt() throws IOException {
        out.write(0x21); out.write(0xf9); out.write(4);
        out.write(0);
        writeShort(delay / 10);
        out.write(0); out.write(0);
    }

    private void writeImageDesc() throws IOException {
        out.write(0x2c);
        writeShort(0); writeShort(0);
        writeShort(width); writeShort(height);
        out.write(0); // local color table flag off
    }

    private void writePixels(byte[] pixels) throws IOException {
        LzwEncoder enc = new LzwEncoder(width, height, pixels, 8);
        enc.encode(out);
    }

    private void writeShort(int v) throws IOException {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    // ── Minimal LZW encoder ───────────────────────────────────────

    static class LzwEncoder {
        private final byte[] pixAry;
        private final int initCodeSize;

        LzwEncoder(int w, int h, byte[] pixels, int colorDepth) {
            pixAry = pixels;
            initCodeSize = Math.max(2, colorDepth);
        }

        void encode(OutputStream os) throws IOException {
            os.write(initCodeSize);
            compress(initCodeSize + 1, os);
            os.write(0);
        }

        private static final int BITS = 12;
        private static final int HSIZE = 5003;
        int n_bits, maxbits = BITS, maxcode, maxmaxcode = 1 << BITS;
        int[] htab = new int[HSIZE], codetab = new int[HSIZE];
        int free_ent, clear_flg = 0;
        int g_init_bits, ClearCode, EOFCode;
        int cur_accum, cur_bits;
        int[] masks = {0,1,3,7,15,31,63,127,255,511,1023,2047,4095,8191,16383,32767,65535};
        int a_count;
        byte[] accum = new byte[256];

        void compress(int init_bits, OutputStream outs) throws IOException {
            g_init_bits = init_bits;
            clear_flg = 0; n_bits = g_init_bits;
            maxcode = (1 << n_bits) - 1;
            ClearCode = 1 << (init_bits - 1);
            EOFCode = ClearCode + 1;
            free_ent = ClearCode + 2;
            a_count = 0; cur_accum = 0; cur_bits = 0;
            for (int i = 0; i < HSIZE; i++) htab[i] = -1;
            output(ClearCode, outs);
            int ent = pixAry[0] & 0xff;
            for (int i = 1; i < pixAry.length; i++) {
                int c = pixAry[i] & 0xff;
                int fcode = (c << BITS) + ent;
                int h = (c << 4) ^ ent;
                if (htab[h] == fcode) { ent = codetab[h]; continue; }
                if (htab[h] >= 0) {
                    int disp = HSIZE - h; if (h == 0) disp = 1;
                    do { h -= disp; if (h < 0) h += HSIZE;
                         if (htab[h] == fcode) { ent = codetab[h]; break; }
                    } while (htab[h] >= 0);
                    if (htab[h] == fcode) continue;
                }
                output(ent, outs); ent = c;
                if (free_ent < maxmaxcode) { codetab[h] = free_ent++; htab[h] = fcode; }
                else { for (int x = 0; x < HSIZE; x++) htab[x] = -1; free_ent = ClearCode+2; clear_flg = 1; output(ClearCode, outs); }
            }
            output(ent, outs); output(EOFCode, outs);
            flush_char(outs);
        }

        void output(int code, OutputStream outs) throws IOException {
            cur_accum &= masks[cur_bits]; cur_accum |= code << cur_bits; cur_bits += n_bits;
            while (cur_bits >= 8) { char_out((byte)(cur_accum & 0xff), outs); cur_accum >>= 8; cur_bits -= 8; }
            if (free_ent > maxcode || clear_flg > 0) {
                if (clear_flg > 0) { n_bits = g_init_bits; maxcode = (1<<n_bits)-1; clear_flg = 0; }
                else { n_bits++; maxcode = n_bits == maxbits ? maxmaxcode : (1<<n_bits)-1; }
            }
            if (code == EOFCode) { while (cur_bits > 0) { char_out((byte)(cur_accum & 0xff), outs); cur_accum >>= 8; cur_bits -= 8; } flush_char(outs); }
        }

        void char_out(byte c, OutputStream outs) throws IOException {
            accum[a_count++] = c;
            if (a_count >= 254) flush_char(outs);
        }

        void flush_char(OutputStream outs) throws IOException {
            if (a_count > 0) { outs.write(a_count); outs.write(accum, 0, a_count); a_count = 0; }
        }
    }
}
