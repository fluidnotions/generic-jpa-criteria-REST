package com.fluidnotions.genericjpacriteriarest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
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


    private final EntityManager entityManager;

    @Operation(summary = "Patch update by table column name on any table", description = "Patch update by table column name on any table independent of project orm mappings. The payload is json property name is column name and value", tags = {"2. generic-jpa-criteria-rest"})
    @PatchMapping("patch/{tableName}/{primaryKey}/{primaryKeyValue}")
    public ResponseEntity<Void> updateTable(
            @PathVariable String tableName, @PathVariable String primaryKey, @PathVariable Object primaryKeyValue, @RequestBody Map<String, Object> params
    ) {
        log.debug("Updating table: {} with params: {}", tableName, params);
        patchTable(tableName, params, primaryKey, primaryKeyValue);
        return ResponseEntity.noContent().build();
    }



    @Transactional
    public void patchTable(String tableName, Map<String, Object> params, String primaryKey, Object primaryKeyValue) {
        if (params.isEmpty()) {
            throw new IllegalArgumentException("No parameters provided for update");
        }
        StringBuilder queryBuilder = new StringBuilder("UPDATE ");
        queryBuilder.append(tableName).append(" SET ");
        params.forEach((key, value) -> {
            queryBuilder.append(key).append(" = :").append(key).append(", ");
        });
        // Remove the last comma and space
        queryBuilder.setLength(queryBuilder.length() - 2);
        queryBuilder.append(" WHERE ").append(primaryKey).append(" = :").append(primaryKey);
        Query query = entityManager.createNativeQuery(queryBuilder.toString());
        params.forEach(query::setParameter);
        query.setParameter(primaryKey, primaryKeyValue);
        query.executeUpdate();
    }
}
