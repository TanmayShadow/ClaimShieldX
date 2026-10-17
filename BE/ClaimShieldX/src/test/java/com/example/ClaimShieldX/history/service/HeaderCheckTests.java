package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.history.csv.CsvColumn;
import com.example.ClaimShieldX.history.csv.CsvTemplate;
import com.example.ClaimShieldX.history.csv.CsvTemplates;
import com.example.ClaimShieldX.history.csv.ImportFileException;
import com.example.ClaimShieldX.history.model.ImportFailureCode;
import com.example.ClaimShieldX.history.model.ImportType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderCheckTests {
    private static final CsvTemplate CLAIMS = CsvTemplates.find(ImportType.HISTORICAL_CLAIMS, "1").orElseThrow();

    private static List<String> allColumns() {
        return CLAIMS.columns().stream().map(CsvColumn::name).collect(Collectors.toCollection(ArrayList::new));
    }

    private static ImportFileException failure(List<String> header) {
        try {
            HistoryImportService.checkedHeader(header, CLAIMS);
        } catch (ImportFileException e) {
            return e;
        }
        throw new AssertionError("Expected the header to be refused: " + header);
    }

    @Test
    void theExactTemplateHeaderPasses() throws Exception {
        assertThat(HistoryImportService.checkedHeader(allColumns(), CLAIMS)).isEqualTo(allColumns());
    }

    @Test
    void columnsMayComeInAnyOrderAndOptionalOnesMayBeLeftOut() throws Exception {
        List<String> shuffled = allColumns();
        Collections.shuffle(shuffled, new java.util.Random(7));
        assertThat(HistoryImportService.checkedHeader(shuffled, CLAIMS)).containsExactlyElementsOf(shuffled);

        assertThat(HistoryImportService.checkedHeader(CLAIMS.requiredColumns(), CLAIMS)).isEqualTo(CLAIMS.requiredColumns());
    }

    @Test
    void namesAreTrimmedAndLowerCased() throws Exception {
        List<String> messy = allColumns().stream().map(c -> "  " + c.toUpperCase() + " ").toList();

        assertThat(HistoryImportService.checkedHeader(messy, CLAIMS)).isEqualTo(allColumns());
    }

    @Test
    void aMissingRequiredColumnIsNamedFromOurTemplate() {
        List<String> header = allColumns();
        header.remove("vehicle_token");
        header.remove("currency");

        ImportFileException e = failure(header);

        assertThat(e.code()).isEqualTo(ImportFailureCode.INVALID_HEADER);
        assertThat(e.detail()).contains("vehicle_token").contains("currency").doesNotContain("source_record_id");
    }

    @Test
    void anUnknownColumnIsOnlyCountedNeverNamedBecauseItsNameCameFromTheFile() {
        List<String> header = allColumns();
        header.add("claimant_full_name");
        header.add("SECRET-DATA-VALUE");

        ImportFileException e = failure(header);

        assertThat(e.detail()).contains("2 column(s)").doesNotContain("claimant_full_name").doesNotContain("SECRET-DATA-VALUE");
    }

    @Test
    void aFileWhoseFirstRowIsDataDoesNotEchoThatDataBack() {
        ImportFileException e = failure(List.of("HC-1", "CLM-HIST-1", "VEH-TOKEN-0001", "2025-03-14T11:00:00+05:30", "COLLISION"));

        assertThat(e.code()).isEqualTo(ImportFailureCode.INVALID_HEADER);
        assertThat(e.detail()).doesNotContain("HC-1").doesNotContain("VEH-TOKEN-0001").doesNotContain("CLM-HIST-1").doesNotContain("2025");
        assertThat(e.detail()).contains("Missing required columns");
    }

    @Test
    void anEmptyOrRepeatedColumnNameIsRefused() {
        List<String> empty = allColumns();
        empty.set(3, "  ");
        List<String> repeated = allColumns();
        repeated.set(4, "Vehicle_Token");

        assertThat(failure(empty).code()).isEqualTo(ImportFailureCode.INVALID_HEADER);
        assertThat(failure(empty).detail()).contains("empty");
        assertThat(failure(repeated).detail()).contains("more than once").doesNotContain("Vehicle_Token");
    }

    @Test
    void aGarageHeaderIsNotAClaimsHeader() {
        List<String> garage = CsvTemplates.find(ImportType.GARAGE_HISTORY, "1").orElseThrow().columns().stream().map(CsvColumn::name).toList();

        assertThatThrownBy(() -> HistoryImportService.checkedHeader(garage, CLAIMS)).isInstanceOf(ImportFileException.class);
    }
}
