package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.model.ImportType;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** One versioned CSV layout. The columns, their order in the downloadable template and their meaning are all stated here. */
public record CsvTemplate(ImportType type, String version, List<CsvColumn> columns, String exampleRow) {
    public Set<String> columnNames() {
        return columns.stream().map(CsvColumn::name).collect(Collectors.toUnmodifiableSet());
    }

    public List<String> requiredColumns() {
        return columns.stream().filter(CsvColumn::required).map(CsvColumn::name).toList();
    }

    /** The header row followed by one made-up example row, as a ready-to-fill file. */
    public String asCsv() {
        return columns.stream().map(CsvColumn::name).collect(Collectors.joining(",")) + "\r\n" + exampleRow + "\r\n";
    }
}
