package com.example.ClaimShieldX.history.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/** What happened to one row of one import. The errors of a rejected row never repeat the values that were wrong. */
@Entity
@Table(name = "historical_import_rows")
@Getter
@Setter
public class HistoricalImportRow {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private RowStatus status;

    @Column(name = "source_record_id", updatable = false)
    private String sourceRecordId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", updatable = false)
    private List<RowError> errors;
}
