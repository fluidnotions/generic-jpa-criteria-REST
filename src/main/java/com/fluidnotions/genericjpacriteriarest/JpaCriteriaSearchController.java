package com.fluidnotions.genericjpacriteriarest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${rest-jpa-criteria-search.controller-path-prefix:/}")
@SuppressWarnings("unchecked")
public class JpaCriteriaSearchController {

    private static final Logger logger = LoggerFactory.getLogger(JpaCriteriaSearchController.class);

    @Value("${rest-jpa-criteria-search.entity-name-fallback-prefix:none}")
    private String entityNameFallbackPrefix;

    private EntityManager entityManager;

    public JpaCriteriaSearchController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }


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


    @Operation(summary = "criteria search on any entity", description = "matches on like string ignoring case, numbers equal, projection list", tags = {"1. generic-jpa-criteria-rest"})
    @PostMapping(value = "/search/{entity}", produces = "application/json", consumes = "application/json")
    public String search(@PathVariable(name = "entity") String entityName, @RequestBody Dto.Search search) throws Exception {
        var entities = entityManager.getMetamodel().getEntities();
        var entityNames = entities.stream().map(e -> e.getName().toLowerCase()).collect(Collectors.joining(", "));
        logger.debug("Entities: {}", entityNames);
        EntityType<?> entityType = getEntityType(entityName, entities);
        logger.debug("Entity: {}", entityType);
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

    private String search(Dto.Search search, EntityType<?> entityType) throws Exception {
        var results = searchEntities(entityType.getJavaType(), entityManager, search);
        if (results.size() > 0) {
            return serializeEntityTypeList(results, entityType, search);
        } else {
            throw new HttpResponseException(HttpStatus.NO_CONTENT);
        }
    }

    private EntityType<?> getEntityType(String entity, Set<EntityType<?>> entities) {
        var entityType = entities.stream().filter(e -> e.getName().equalsIgnoreCase(entity)).findFirst().orElse(null);
        return entityType;
    }

    private String serializeEntityTypeList(List<?> results, EntityType<?> entityType, Dto.Search search) throws JsonProcessingException {
        var objectMapper = objectMapper();

        if (search.projection() != null && search.projection().length > 0) {
            SimpleModule module = new SimpleModule();
            module.addSerializer(entityType.getJavaType(), new JsonSerializer<Object>() {
                @Override
                public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    ObjectMapper defaultMapper = objectMapper();
                    ObjectNode objectNode = defaultMapper.valueToTree(value);
                    Iterator<String> fieldNameIter = objectNode.fieldNames();

                    // Create a map to hold lower-case to original-case field name mapping
                    Map<String, String> fieldNameMap = new HashMap<>();
                    fieldNameIter.forEachRemaining(fieldName -> fieldNameMap.put(fieldName.toLowerCase(), fieldName));

                    List<String> fieldNames = new ArrayList<>(fieldNameMap.keySet());

                    String[] projection = search.projection();
                    var include = Arrays.stream(projection).map(String::toLowerCase).collect(Collectors.toList());

                    for (String fieldName : fieldNames) {
                        if (!include.contains(fieldName)) {
                            // Use the original-case field name for the remove operation
                            objectNode.remove(fieldNameMap.get(fieldName));
                        }
                    }
                    gen.writeTree(objectNode);
                }
            });

            objectMapper.registerModule(module);
        }

        var javaTypeList = TypeFactory.defaultInstance().constructCollectionType(List.class, entityType.getJavaType());
        var writer = objectMapper.writerFor(javaTypeList);
        var json = writer.writeValueAsString(results);
        return json;
    }


    private List<?> searchEntities(Class<?> domainClass, EntityManager entityManager, Dto.Search search) {
        var whereIsPresent = searchRecordValidation(search);

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<?> criteriaQuery = criteriaBuilder.createQuery(domainClass);
        Root<?> root = criteriaQuery.from(domainClass);

        List<Predicate> predicates = new ArrayList<>();
        Field[] fields = domainClass.getDeclaredFields();
        if (whereIsPresent && search.where().like() != null) {
            addLikePredicates(search.where().like(), criteriaBuilder, root, predicates, fields);
        }
        if (whereIsPresent && search.where().equalsLong() != null) {
            addEqualsLongPredicates(search.where().equalsLong(), criteriaBuilder, root, predicates, fields);
        }
        if (predicates.size() > 0) {
            criteriaQuery.where(predicates.toArray(new Predicate[predicates.size()]));
        }
        var query = entityManager.createQuery(criteriaQuery);
        var results = query.getResultList();
        return results;
    }

    private void addLikePredicates(Map<String, String> search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.entrySet()) {
            Arrays.stream(fields).filter(f -> f.getName().equalsIgnoreCase(entry.getKey())).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                Path rootName = root.get(f.getName());
                var value = entry.getValue();
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(rootName), ("%" + value.toLowerCase() + "%")));
            });
        }
    }

    private void addEqualsLongPredicates(Map<String, Long> search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.entrySet()) {
            Arrays.stream(fields).filter(f -> f.getName().equalsIgnoreCase(entry.getKey())).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                Path rootName = root.get(f.getName());
                var value = entry.getValue();
                predicates.add(criteriaBuilder.equal(rootName, value));
            });
        }
    }

    private boolean searchRecordValidation(Dto.Search models) {
        var whereIsPresent = models.where() != null;
        var projectionIsEmpty = models.projection() == null;
        var likeIsEmpty = whereIsPresent && models.where().like() != null;
        var equalsLongIsEmpty = whereIsPresent && models.where().equalsLong() == null;

        if (projectionIsEmpty && likeIsEmpty && equalsLongIsEmpty) {
            throw new IllegalStateException("search.where().like(), search.where().equalsLong(), and search.projection are all empty, which is not supported");
        }
        return whereIsPresent;
    }


}
