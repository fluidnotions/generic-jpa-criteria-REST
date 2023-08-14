package com.fluidnotions.genericjpacriteriarest;

import java.util.Map;
import java.util.Set;

public interface Dto {

    record Search(Where where, String[] projection) {
    }

    record Where(Map<String, String> like, Map<String, Long> equalsLong, Map<String, String> equalsString, Set<String> isNull, Set<String> isNotNull) {
    }
}
