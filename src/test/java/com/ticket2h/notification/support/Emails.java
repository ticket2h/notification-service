package com.ticket2h.notification.support;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** Đọc nội dung email trong test: HTML, chữ thường, và giải mã ảnh QR đính kèm inline. */
public final class Emails {

    public static String html(Part part) {
        return collectText(part, "text/html");
    }

    public static String plainText(Part part) {
        return collectText(part, "text/plain");
    }

    /** Chuỗi trong từng ảnh QR inline (ảnh PNG có Content-ID), theo thứ tự trong email. */
    public static List<String> qrCodes(Part part) {
        List<String> codes = new ArrayList<>();
        collectQr(part, codes);
        return codes;
    }

    private static void collectQr(Part part, List<String> codes) {
        try {
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    collectQr(multipart.getBodyPart(i), codes);
                }
            } else if (part.isMimeType("image/png") && part instanceof BodyPart body
                    && body.getHeader("Content-ID") != null) {
                try (InputStream in = part.getInputStream()) {
                    BufferedImage image = ImageIO.read(in);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
                    codes.add(new QRCodeReader().decode(bitmap).getText());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được ảnh QR trong email", e);
        }
    }

    private static String collectText(Part part, String mimeType) {
        try {
            if (part.isMimeType(mimeType)) {
                return (String) part.getContent();
            }
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < multipart.getCount(); i++) {
                    text.append(collectText(multipart.getBodyPart(i), mimeType));
                }
                return text.toString();
            }
            return "";
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được nội dung email", e);
        }
    }

    private Emails() {}
}
