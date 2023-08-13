package com.fluidnotions.genericjpacriteriarest;

import java.util.Map;

public interface Dto {

    record Search(Where where, String[] projection) {
    }

    record Where(Map<String, String> like, Map<String, Long> equalsLong) {
    }
}
