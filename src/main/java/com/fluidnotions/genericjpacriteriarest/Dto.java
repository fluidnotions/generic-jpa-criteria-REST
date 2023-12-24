package com.fluidnotions.genericjpacriteriarest;

import jakarta.persistence.metamodel.EntityType;
import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Dto {

    @Builder
    record Search(Where where, String[] projection) {
    }

    @Builder
    record Where(Map<String, String> like, Map<String, Long> equalsLong, Map<String, Long> notEqualsLong, Map<String, String> equalsString, Set<String> isNull, Set<String> isNotNull) {
    }

    @Builder
    record SearchResult(List<?> results, EntityType<?> entityType) {
    }
}
