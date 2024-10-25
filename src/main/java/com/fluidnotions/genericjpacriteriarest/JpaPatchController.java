package com.fluidnotions.genericjpacriteriarest;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController()
@RequestMapping("${rest-jpa-criteria-search.controller-path-prefix:/}")
public class JpaPatchController {


    private final JpaPatchService jpaPatchService;

    @Operation(summary = "Patch update by table column name on any table", description = "Patch update by table column name on any table independent of project orm mappings. The payload is json property name is column name and value", tags = {"2. generic-jpa-criteria-rest"})
    @PatchMapping("patch/{tableName}/{primaryKey}/{primaryKeyValue}")
    public ResponseEntity<Void> updateTable(
            @PathVariable String tableName, @PathVariable String primaryKey, @PathVariable Object primaryKeyValue, @RequestBody Map<String, Object> params
    ) {
        log.debug("Updating table: {} with params: {}", tableName, params);
        jpaPatchService.patchTable(tableName, params, primaryKey, primaryKeyValue);
        return ResponseEntity.noContent().build();
    }




}
