package com.example.ClaimShieldX.extraction.schema;

/** A problem the model noticed with the document itself. The code is one of a fixed list (see the prompt). */
public record ModelWarning(String code, Integer page, String quote) {
}
