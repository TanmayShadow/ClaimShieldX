package com.example.ClaimShieldX.documents.service;

import com.example.ClaimShieldX.support.TestFiles;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DetectedFileTypeTests {
    @Test
    void identifiesEachAcceptedTypeFromItsFirstBytes() {
        assertThat(DetectedFileType.detect(TestFiles.pdf(1))).contains(DetectedFileType.PDF);
        assertThat(DetectedFileType.detect(TestFiles.jpeg(1))).contains(DetectedFileType.JPEG);
        assertThat(DetectedFileType.detect(TestFiles.png(1))).contains(DetectedFileType.PNG);
    }

    @Test
    void reportsTheContentTypeAndExtensionOfEachType() {
        assertThat(DetectedFileType.PDF.contentType()).isEqualTo("application/pdf");
        assertThat(DetectedFileType.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(DetectedFileType.PNG.contentType()).isEqualTo("image/png");
        assertThat(DetectedFileType.JPEG.extension()).isEqualTo("jpg");
    }

    @Test
    void rejectsExecutablesArchivesAndPlainText() {
        assertThat(DetectedFileType.detect(TestFiles.windowsExecutable())).isEmpty();
        assertThat(DetectedFileType.detect(TestFiles.zipArchive())).isEmpty();
        assertThat(DetectedFileType.detect(TestFiles.plainText())).isEmpty();
    }

    @Test
    void rejectsTooShortOrEmptyInput() {
        assertThat(DetectedFileType.detect(new byte[0])).isEmpty();
        assertThat(DetectedFileType.detect(new byte[]{0x25, 0x50})).isEmpty();
        assertThat(DetectedFileType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
    }

    @Test
    void aSignatureMustBeAtTheStartNotSomewhereInside() {
        byte[] shifted = new byte[20];
        System.arraycopy(TestFiles.pdf(1), 0, shifted, 3, 10);
        assertThat(DetectedFileType.detect(shifted)).isEmpty();
    }

    @Test
    void aPartialSignatureIsNotEnough() {
        byte[] almostPng = Arrays.copyOf(TestFiles.png(1), 8);
        almostPng[7] = 0x00;
        Optional<DetectedFileType> detected = DetectedFileType.detect(almostPng);
        assertThat(detected).isEmpty();
    }

    @Test
    void exactlyTheSignatureBytesAreEnough() {
        assertThat(DetectedFileType.detect("%PDF-".getBytes())).contains(DetectedFileType.PDF);
    }
}
