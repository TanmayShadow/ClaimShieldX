package com.example.ClaimShieldX.common;

import com.example.ClaimShieldX.common.utils.PageParams;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageParamsTests {
    private static final Map<String, String> SORTABLE = Map.of("receivedAt", "receivedAt", "fileName", "fileName");

    private static Pageable resolve(Integer page, Integer size, String sort) {
        return PageParams.resolve(page, size, sort, SORTABLE, "receivedAt", Sort.Direction.DESC);
    }

    private static List<String> fieldsOfIssues(Throwable error) {
        return ((RequestValidationException) error).getIssues().stream().map(issue -> issue.field()).toList();
    }

    @Test
    void defaultsAreTheFirstPageOfTwentyNewestFirst() {
        Pageable pageable = resolve(null, null, null);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("receivedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void theIdIsAlwaysATieBreakerSoPagesAreStable() {
        assertThat(resolve(null, null, "fileName,desc").getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void explicitValuesAreHonoured() {
        Pageable pageable = resolve(3, 100, "fileName,desc");

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("fileName").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void aSortWithoutADirectionIsAscendingAndTheDirectionIsCaseInsensitive() {
        assertThat(resolve(null, null, "fileName").getSort().getOrderFor("fileName").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(resolve(null, null, "fileName,DESC").getSort().getOrderFor("fileName").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 1000, Integer.MAX_VALUE})
    void aSizeOutsideOneToOneHundredIsRejected(int size) {
        assertThatThrownBy(() -> resolve(null, size, null)).isInstanceOf(RequestValidationException.class);
    }

    @Test
    void theLargestAndSmallestAllowedSizesWork() {
        assertThat(resolve(null, 1, null).getPageSize()).isEqualTo(1);
        assertThat(resolve(null, 100, null).getPageSize()).isEqualTo(100);
    }

    @Test
    void aNegativePageIsRejected() {
        assertThatThrownBy(() -> resolve(-1, null, null)).satisfies(error -> assertThat(fieldsOfIssues(error)).containsExactly("page"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "id", "organizationId", "receivedAt;drop table documents", "receivedAt desc", "x,asc", "RECEIVEDAT"})
    void onlyTheListedFieldsCanBeSortedOn(String sort) {
        assertThatThrownBy(() -> resolve(null, null, sort)).satisfies(error -> assertThat(fieldsOfIssues(error)).contains("sort"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fileName,sideways", "fileName,asc,desc", "fileName,"})
    void aMalformedDirectionIsRejected(String sort) {
        assertThatThrownBy(() -> resolve(null, null, sort)).isInstanceOf(RequestValidationException.class);
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        assertThatThrownBy(() -> resolve(-5, 500, "nope")).satisfies(error -> assertThat(fieldsOfIssues(error)).contains("page", "size", "sort"));
    }
}
