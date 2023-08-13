package com.fluidnotions.genericjpacriteriaREST;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@RequiredArgsConstructor
@RestController
@ConditionalOnProperty(name = "rest-jpa-criteria-search.advanced", havingValue = "true", matchIfMissing = false)
public class JpaCriteriaSearchController {

    private final EntityManager entityManager;


    @Value("${rest-jpa-criteria-search.entity-name-fallback-prefix:none}")
    private String entityNameFallbackPrefix;


 


    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        @JsonIgnoreProperties({"hibernateLazyInitializer"})
        record HibernateMixin() {
        }

        builder.mixIn(Object.class, HibernateMixin.class);
        ObjectMapper objectMapper = builder.build();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return objectMapper;
    }

    @Operation(summary = "criteria search on any entity", description = "Currently only supports eq within where request body. Projection is not implemented.", tags = {"1. jpa-criteria-search"})
    @PutMapping(value = "/search/{entity}", produces = "application/json", consumes = "application/json")
    public String search(@PathVariable(name = "entity") String entityName, @RequestBody Search search) throws Exception {
        var entities = entityManager
                .getMetamodel()
                .getEntities();
        var entityNames = entities.stream().map(e -> e.getName().toLowerCase()).collect(Collectors.joining(", "));
        log.debug("Entities: {}", entityNames);
        EntityType<?> entityType = getEntityType(entityName, entities);
        log.debug("Entity: {}", entityType);
        if (entityType != null) {
            return search(search, entityType);
        } else if (!entityNameFallbackPrefix.equals("none")) {
            entityType = getEntityType(entityNameFallbackPrefix + entityName, entities);
            if (entityType != null) {
                return search(search, entityType);
            }
        }
        throw new HttpResponseException("No entity type '%s' found".formatted(entityName), HttpStatus.BAD_REQUEST);
    }

    private String search(Search search, EntityType<?> entityType) throws Exception {
        var results = searchEntities(entityType.getJavaType(), entityManager, search);
        if (results.size() > 0) {
            return serializeEntityTypeList(results, entityType);
        } else {
            throw new HttpResponseException(HttpStatus.NO_CONTENT);
        }
    }

    private EntityType<?> getEntityType(String entity, Set<EntityType<?>> entities) {
        var entityType = entities.stream()
                .filter(e -> e.getName().equalsIgnoreCase(entity))
                .findFirst()
                .orElse(null);
        return entityType;
    }

    private String serializeEntityTypeList(List<?> results, EntityType<?> entityType) throws JsonProcessingException {
        var javaTypeList = TypeFactory.defaultInstance().constructCollectionType(List.class, entityType.getJavaType());
        var writer = objectMapper().writerFor(javaTypeList);
        var json = writer.writeValueAsString(results);
        return json;
    }

    private List<?> searchEntities(Class<?> domainClass, EntityManager entityManager, Search search) throws Exception {

        search.validate();
        search.where().validate();

        if (search.where().eq().isEmpty()) {
            throw new IllegalStateException("search.where().eq() is empty, this is currently not supported since other conditionals have not been implemented yet.");
        }

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<?> criteriaQuery = criteriaBuilder.createQuery(domainClass);
        Root<?> root = criteriaQuery.from(domainClass);

        List<Predicate> predicates = new ArrayList<>();
        Field[] fields = domainClass.getDeclaredFields();
        for (var entry : search.where().eq().get().entrySet()) {
            Arrays.stream(fields)
                    .filter(f -> f.getName().equals(entry.getKey()))
                    .findFirst()
                    .ifPresent(f -> {
                        f.setAccessible(true);
                        var rootName = root.get(f.getName());
                        var value = entry.getValue();
                        predicates.add(criteriaBuilder.equal(rootName, value));
                    });
        }
        criteriaQuery.where(predicates.toArray(new Predicate[predicates.size()]));
        var query = entityManager.createQuery(criteriaQuery);
        var results = query.getResultList();
        return results;
    }

    record Search(Where where, Optional<Projection> projection) {
        public void validate() throws Exception {
            if (projection.isPresent()) {
                throw new Exception("Projection is not implemented");
            }
        }
    }

    record OptionalWithFieldName(String fieldName, Optional<?> optional) {
    }

    record Where(Optional<Map<String, String>> eq,
                 Optional<Map<String, String>> like,
                 Optional<Map<String, String>> notLike,
                 Optional<Map<String, String>> in,
                 Optional<Map<String, String>> notIn) {
        public void validate() throws Exception {
            List<OptionalWithFieldName> options = List.of(new OptionalWithFieldName("like", like), new OptionalWithFieldName("notLike", notLike), new OptionalWithFieldName("in", in), new OptionalWithFieldName("notIn", notIn));
            for (OptionalWithFieldName option : options) {
                if (option.optional().isPresent()) {
                    throw new Exception("while.%s is not implemented".formatted(option.fieldName()));
                }
            }
        }
    }

    record Projection(Optional<Map<String, String>> include, Optional<Map<String, String>> exclude) {
    }
}
