package com.example.ClaimShieldX.extraction.support;

import com.example.ClaimShieldX.extraction.schema.*;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Builds the answers a model would give, as typed objects, so each test changes exactly the fact it is about. */
public final class TestDocuments {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private TestDocuments() {
    }

    public static ModelField f(String value, int page, String quote) {
        return new ModelField(value, page, quote, null);
    }

    public static ModelField f(String value) {
        return f(value, 1, value);
    }

    public static ModelField unknown(String reason) {
        return new ModelField(null, null, null, reason);
    }

    public static ModelField none() {
        return unknown("NOT_PRESENT");
    }

    public static String json(ModelDocument document) {
        try {
            return MAPPER.writeValueAsString(document);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** One consistent invoice: three lines summing to 21,000.00, 18% tax, grand total 24,780.00. */
    public static ModelDocument invoice(String garage, String vehicle, String documentDate) {
        return new ModelDocument(
                new ModelHeader(f("INV-2026-0042", 1, "Invoice No: INV-2026-0042"), f(documentDate, 1, "Date: " + documentDate), none(), none(), none(),
                        f(garage, 1, "Garage: " + garage), f(vehicle, 1, "Vehicle: " + vehicle), f("INR", 1, "All amounts in INR"),
                        f("Front bumper replaced and repainted", 1, "Front bumper replaced and repainted")),
                List.of(line("Front bumper", "Parts", "1", "12,000.00", "12,000.00"),
                        line("Paint and finish", "Paint", "1", "4,500.00", "4,500.00"),
                        line("Labour", "Labour", "3", "1,500.00", "4,500.00")),
                new ModelTotals(f("21,000.00", 1, "Sub total 21,000.00"), f("3,780.00", 1, "GST 18% 3,780.00"), none(), f("24,780.00", 1, "Grand total 24,780.00")),
                List.of());
    }

    public static ModelLineItem line(String description, String category, String quantity, String unitPrice, String lineTotal) {
        return new ModelLineItem(1, description + " " + lineTotal, description, category, quantity, unitPrice, null, lineTotal);
    }

    public static ModelDocument with(ModelDocument document, UnaryOperator<Builder> change) {
        return change.apply(new Builder(document)).build();
    }

    /** Copy-and-change for the immutable records. */
    public static final class Builder {
        public ModelHeader header;
        public List<ModelLineItem> lineItems;
        public ModelTotals totals;
        public List<ModelWarning> warnings;

        Builder(ModelDocument d) {
            this.header = d.header();
            this.lineItems = new ArrayList<>(d.lineItems());
            this.totals = d.totals();
            this.warnings = new ArrayList<>(d.warnings());
        }

        public Builder header(ModelHeader value) {
            header = value;
            return this;
        }

        public Builder totals(ModelTotals value) {
            totals = value;
            return this;
        }

        public Builder lines(List<ModelLineItem> value) {
            lineItems = new ArrayList<>(value);
            return this;
        }

        public Builder warn(String code, Integer page, String quote) {
            warnings.add(new ModelWarning(code, page, quote));
            return this;
        }

        public Builder garage(ModelField value) {
            header = new ModelHeader(header.documentNumber(), header.documentDate(), header.incidentDate(), header.repairStartDate(),
                    header.repairCompletionDate(), value, header.vehicleIdentifier(), header.currency(), header.repairDescription());
            return this;
        }

        public Builder vehicle(ModelField value) {
            header = new ModelHeader(header.documentNumber(), header.documentDate(), header.incidentDate(), header.repairStartDate(),
                    header.repairCompletionDate(), header.garageIdentifier(), value, header.currency(), header.repairDescription());
            return this;
        }

        public Builder documentDate(ModelField value) {
            header = new ModelHeader(header.documentNumber(), value, header.incidentDate(), header.repairStartDate(),
                    header.repairCompletionDate(), header.garageIdentifier(), header.vehicleIdentifier(), header.currency(), header.repairDescription());
            return this;
        }

        public Builder incidentDate(ModelField value) {
            header = new ModelHeader(header.documentNumber(), header.documentDate(), value, header.repairStartDate(),
                    header.repairCompletionDate(), header.garageIdentifier(), header.vehicleIdentifier(), header.currency(), header.repairDescription());
            return this;
        }

        public Builder currency(ModelField value) {
            header = new ModelHeader(header.documentNumber(), header.documentDate(), header.incidentDate(), header.repairStartDate(),
                    header.repairCompletionDate(), header.garageIdentifier(), header.vehicleIdentifier(), value, header.repairDescription());
            return this;
        }

        public Builder documentNumber(ModelField value) {
            header = new ModelHeader(value, header.documentDate(), header.incidentDate(), header.repairStartDate(),
                    header.repairCompletionDate(), header.garageIdentifier(), header.vehicleIdentifier(), header.currency(), header.repairDescription());
            return this;
        }

        public Builder repairDates(ModelField start, ModelField end) {
            header = new ModelHeader(header.documentNumber(), header.documentDate(), header.incidentDate(), start, end,
                    header.garageIdentifier(), header.vehicleIdentifier(), header.currency(), header.repairDescription());
            return this;
        }

        public Builder grandTotal(ModelField value) {
            totals = new ModelTotals(totals.subtotal(), totals.taxTotal(), totals.discountTotal(), value);
            return this;
        }

        public Builder subtotal(ModelField value) {
            totals = new ModelTotals(value, totals.taxTotal(), totals.discountTotal(), totals.grandTotal());
            return this;
        }

        public Builder taxTotal(ModelField value) {
            totals = new ModelTotals(totals.subtotal(), value, totals.discountTotal(), totals.grandTotal());
            return this;
        }

        public Builder discountTotal(ModelField value) {
            totals = new ModelTotals(totals.subtotal(), totals.taxTotal(), value, totals.grandTotal());
            return this;
        }

        ModelDocument build() {
            return new ModelDocument(header, lineItems, totals, warnings);
        }
    }
}
