package com.example.ClaimShieldX.extraction.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;

/** Counts the pages of a PDF so cited page numbers can be checked against the real document. */
final class PdfPages {
    static class UnreadablePdfException extends Exception {
        UnreadablePdfException(Throwable cause) {
            super("The PDF could not be read", cause);
        }
    }

    private PdfPages() {
    }

    static int count(byte[] pdf) throws UnreadablePdfException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            if (pages < 1) {
                throw new UnreadablePdfException(null);
            }
            return pages;
        } catch (IOException | RuntimeException e) {
            throw new UnreadablePdfException(e);
        }
    }
}
