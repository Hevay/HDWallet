package com.vx.hdwallet;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.nio.ByteBuffer;
import java.util.Hashtable;

public class Qr {

    private static final QRCodeWriter QR_CODE_WRITE = new QRCodeWriter();

    //传入字符串内容，生成对应的二维码
    public static Bitmap bitmap(final String conent) {
        try {
            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            BitMatrix result = QR_CODE_WRITE.encode(conent, BarcodeFormat.QR_CODE, 0, 0, hints);
            int width = result.getWidth();
            int height = result.getHeight();
            byte[] pixels = new byte[width * height];
            for (int y = 0; y < height; y++) {
                //设定偏移量， 外层循环变量y是第几行
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = (byte) (result.get(x, y) ? -1 : 0);
                }
            }

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
        }
        return null;
    }
}
