package com.example.ClaimShieldX.common.utils;

import com.example.ClaimShieldX.common.ErrorResponse;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns page, size and sort query parameters into a Pageable, with a hard maximum size and a fixed list of
 * sortable fields, so a client can never sort on an arbitrary column or request an unbounded page.
 */
public final class PageParams {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    /** @param sortableFields public sort name mapped to the entity property */
    public static Pageable resolve(Integer page, Integer size, String sort, Map<String, String> sortableFields,
                                   String defaultSortName, Sort.Direction defaultDirection) {
        List<ErrorResponse.FieldIssue> issues = new ArrayList<>();
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            issues.add(new ErrorResponse.FieldIssue("page", "must be 0 or greater"));
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            issues.add(new ErrorResponse.FieldIssue("size", "must be between 1 and " + MAX_SIZE));
        }

        String sortName = defaultSortName;
        Sort.Direction direction = defaultDirection;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            sortName = parts[0].strip();
            if (!sortableFields.containsKey(sortName)) {
                issues.add(new ErrorResponse.FieldIssue("sort", "must be one of " + String.join(", ", sortableFields.keySet())));
            }
            if (parts.length > 2) {
                issues.add(new ErrorResponse.FieldIssue("sort", "must look like field,asc or field,desc"));
            } else if (parts.length == 2) {
                String requested = parts[1].strip().toLowerCase(Locale.ROOT);
                if (requested.equals("asc")) {
                    direction = Sort.Direction.ASC;
                } else if (requested.equals("desc")) {
                    direction = Sort.Direction.DESC;
                } else {
                    issues.add(new ErrorResponse.FieldIssue("sort", "direction must be asc or desc"));
                }
            } else {
                direction = Sort.Direction.ASC;
            }
        }
        if (!issues.isEmpty()) {
            throw new RequestValidationException(issues);
        }
        // The id is a tie-breaker so pages never repeat or skip rows when the sort field has equal values.
        Sort ordering = Sort.by(new Sort.Order(direction, sortableFields.get(sortName)), Sort.Order.asc("id"));
        return PageRequest.of(pageNumber, pageSize, ordering);
    }
}
