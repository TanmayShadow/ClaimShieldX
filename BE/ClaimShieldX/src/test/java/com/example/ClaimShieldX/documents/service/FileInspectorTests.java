package com.example.ClaimShieldX.documents.service;

import com.example.ClaimShieldX.exceptions.EmptyFileException;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import com.example.ClaimShieldX.exceptions.UnsupportedFileTypeException;
import com.example.ClaimShieldX.support.TestFiles;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileInspectorTests {
    private static MultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "anything.pdf", "application/pdf", content);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @Test
    void reportsTheChecksumSizeAndTypeOfAValidFile() throws Exception {
        byte[] content = TestFiles.pdf(7);

        FileInspector.Inspection inspection = FileInspector.inspect(upload(content));

        assertThat(inspection.type()).isEqualTo(DetectedFileType.PDF);
        assertThat(inspection.sizeBytes()).isEqualTo(content.length);
        assertThat(inspection.sha256()).isEqualTo(sha256(content)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void theChecksumIsStableAndDependsOnlyOnTheContent() {
        byte[] content = TestFiles.jpeg(3);

        assertThat(FileInspector.inspect(upload(content)).sha256()).isEqualTo(FileInspector.inspect(upload(content.clone())).sha256());
        assertThat(FileInspector.inspect(upload(TestFiles.jpeg(4))).sha256()).isNotEqualTo(FileInspector.inspect(upload(content)).sha256());
    }

    @Test
    void theDeclaredTypeAndFileNameAreIgnored() {
        MultipartFile jpegPretendingToBePdf = new MockMultipartFile("file", "invoice.pdf", "application/pdf", TestFiles.jpeg(1));

        assertThat(FileInspector.inspect(jpegPretendingToBePdf).type()).isEqualTo(DetectedFileType.JPEG);
    }

    @Test
    void aWindowsProgramNamedLikeAPdfIsRejected() {
        MultipartFile exe = new MockMultipartFile("file", "invoice.pdf", "application/pdf", TestFiles.windowsExecutable());

        assertThatThrownBy(() -> FileInspector.inspect(exe)).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void textArchivesAndTinyFilesAreRejected() {
        assertThatThrownBy(() -> FileInspector.inspect(upload(TestFiles.plainText()))).isInstanceOf(UnsupportedFileTypeException.class);
        assertThatThrownBy(() -> FileInspector.inspect(upload(TestFiles.zipArchive()))).isInstanceOf(UnsupportedFileTypeException.class);
        assertThatThrownBy(() -> FileInspector.inspect(upload(new byte[]{1, 2, 3}))).isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void anEmptyFileIsRejectedAsEmptyNotAsAnUnsupportedType() {
        assertThatThrownBy(() -> FileInspector.inspect(upload(new byte[0]))).isInstanceOf(EmptyFileException.class);
    }

    @Test
    void aFileOfExactlyTheSignatureIsAccepted() {
        assertThat(FileInspector.inspect(upload("%PDF-".getBytes())).sizeBytes()).isEqualTo(5);
    }

    @Test
    void aLargeFileIsReadAsAStreamAndCountedExactly() {
        byte[] content = TestFiles.pdfOfSize(11, 6 * 1024 * 1024);

        assertThat(FileInspector.inspect(upload(content)).sizeBytes()).isEqualTo(6L * 1024 * 1024);
    }

    @Test
    void aReadFailureBecomesAValidationErrorNotAServerError() {
        MultipartFile broken = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1}) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("connection reset");
            }
        };

        assertThatThrownBy(() -> FileInspector.inspect(broken)).isInstanceOf(RequestValidationException.class);
    }
}
