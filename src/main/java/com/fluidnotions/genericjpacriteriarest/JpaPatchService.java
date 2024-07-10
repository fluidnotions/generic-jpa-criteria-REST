package com.fluidnotions.genericjpacriteriarest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class JpaPatchService {


    private final EntityManager entityManager;

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
