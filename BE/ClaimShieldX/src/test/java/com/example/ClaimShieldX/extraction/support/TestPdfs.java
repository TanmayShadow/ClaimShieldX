package com.example.ClaimShieldX.extraction.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** Small real files (not just the right first bytes): extraction opens PDFs to count their pages. */
public final class TestPdfs {
    private TestPdfs() {
    }

    /** A valid PDF with blank pages; different seeds give different bytes and so different checksums. */
    public static byte[] pdf(int pages, int seed) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.getDocumentInformation().setTitle("test-document-" + seed);
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] png(int seed) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 24; x++) {
                for (int y = 0; y < 24; y++) {
                    image.setRGB(x, y, (seed * 7919 + x * 31 + y * 17) & 0xFFFFFF);
                }
            }
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
