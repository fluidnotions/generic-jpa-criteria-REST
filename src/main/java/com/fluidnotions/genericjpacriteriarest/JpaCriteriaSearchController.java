package com.fluidnotions.genericjpacriteriarest;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("${rest-jpa-criteria-search.controller-path-prefix:/}")
@SuppressWarnings("unchecked")
public class JpaCriteriaSearchController {

    private final JpaCriteriaSearchService jpaCriteriaSearchService;

    @Operation(summary = "criteria search on any entity", description = "matches on like string ignoring case, numbers equal, projection list", tags = {"1. generic-jpa-criteria-rest"})
    @PostMapping(value = "/search/{entity}", produces = "application/json", consumes = "application/json")
    public String search(@PathVariable(name = "entity") String entityName, @RequestBody Dto.Search search) throws HttpResponseException {
        try {
            return jpaCriteriaSearchService.searchAndSerialize(entityName, search);
        } catch (Exception e) {
            throw new HttpResponseException("Error occurred searching :\n" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }





}
