package com.example.ClaimShieldX.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileNameSanitizerTests {
    private static String sanitize(String name) {
        return FileNameSanitizer.sanitize(name, DetectedFileType.PDF);
    }

    @Test
    void anOrdinaryNameIsKept() {
        assertThat(sanitize("invoice-2026.pdf")).isEqualTo("invoice-2026.pdf");
    }

    @Test
    void directoryPartsAreRemoved() {
        assertThat(sanitize("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(sanitize("C:\\Users\\someone\\bill.pdf")).isEqualTo("bill.pdf");
        assertThat(sanitize("/absolute/path/estimate.pdf")).isEqualTo("estimate.pdf");
        assertThat(sanitize("a/b/c.png")).isEqualTo("c.png");
    }

    @Test
    void controlCharactersAreRemoved() {
        assertThat(sanitize("bad\u0000na\nme\t.pdf")).isEqualTo("badname.pdf");
    }

    @Test
    void textDirectionOverridesAndZeroWidthCharactersAreRemoved() {
        assertThat(sanitize("invoice\u202Efdp.exe")).isEqualTo("invoicefdp.exe");
        assertThat(sanitize("in\u200Bvoice\u200D.pdf")).isEqualTo("invoice.pdf");
    }

    @Test
    void leadingDotsAreRemovedSoTheNameIsNotAHiddenFile() {
        assertThat(sanitize(".hidden.pdf")).isEqualTo("hidden.pdf");
        assertThat(sanitize("...pdf")).isEqualTo("pdf");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "....", "/", "\\", "\u0000\u0001", "../.."})
    void aNameWithNothingUsableBecomesAGenericOneWithTheRealExtension(String name) {
        assertThat(FileNameSanitizer.sanitize(name, DetectedFileType.PDF)).isEqualTo("document.pdf");
        assertThat(FileNameSanitizer.sanitize(name, DetectedFileType.JPEG)).isEqualTo("document.jpg");
        assertThat(FileNameSanitizer.sanitize(name, DetectedFileType.PNG)).isEqualTo("document.png");
    }

    @Test
    void whitespaceIsCollapsed() {
        assertThat(sanitize("my    final \t invoice .pdf")).isEqualTo("my final invoice .pdf");
    }

    @Test
    void nonLatinNamesAreKept() {
        assertThat(sanitize("रसीद-नमूना.pdf")).isEqualTo("रसीद-नमूना.pdf");
    }

    @Test
    void aLongNameIsCutToTheColumnLimit() {
        assertThat(sanitize("a".repeat(400) + ".pdf")).hasSize(255);
    }

    @Test
    void cuttingNeverSplitsACharacterMadeOfTwoUnits() {
        String name = "a".repeat(254) + "\uD83D\uDE00" + "b".repeat(10);
        String result = sanitize(name);

        assertThat(result.codePointCount(0, result.length())).isEqualTo(255);
        assertThat(result).endsWith("\uD83D\uDE00");
        assertThat(Character.isHighSurrogate(result.charAt(result.length() - 1))).isFalse();
    }
}
