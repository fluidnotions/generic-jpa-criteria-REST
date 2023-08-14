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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(JpaCriteriaSearchController.class);

    @Value("${rest-jpa-criteria-search.entity-name-fallback-prefix:none}")
    private String entityNameFallbackPrefix;

    @Autowired
    private EntityManager entityManager;

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
        if(whereIsPresent && search.where().isNotNull() != null) {
            addIsNotNullPredicates(search, fields, root, criteriaBuilder, predicates);
        }
        if(whereIsPresent && search.where().isNull() != null){
            addIsNullPredicates(search, fields, root, criteriaBuilder, predicates);
        }
        if(whereIsPresent && search.where().equalsString() != null){
            addEqualsStringPredicates(search, criteriaBuilder, root, predicates, fields);
        }
        if (predicates.size() > 0) {
            criteriaQuery.where(predicates.toArray(new Predicate[predicates.size()]));
        }
        var query = entityManager.createQuery(criteriaQuery);
        var results = query.getResultList();
        return results;
    }

    private void addIsNullPredicates(Dto.Search search, Field[] fields, Root<?> root, CriteriaBuilder criteriaBuilder, List<Predicate> predicates) {
        for (var fieldName : search.where().isNull()) {
            Arrays.stream(fields).filter(f -> {
                var eq = f.getName().equalsIgnoreCase(fieldName);
                log.debug("addIsNullPredicates: eq: {}", eq);
                return eq;
            }).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                var fname = f.getName();
                Path rootName = root.get(fname);
                log.debug("addIsNullPredicates: fieldName: {}, rootName: {}", fname, rootName);
                var predicate = criteriaBuilder.isNull(rootName);
                log.debug("addIsNullPredicates: predicate: {}", predicate);
                predicates.add(predicate);
            });
        }
    }

    private void addIsNotNullPredicates(Dto.Search search, Field[] fields, Root<?> root, CriteriaBuilder criteriaBuilder, List<Predicate> predicates) {
        for (var fieldName : search.where().isNotNull()) {
            Arrays.stream(fields).filter(f -> {
                var eq = f.getName().equalsIgnoreCase(fieldName);
                log.debug("addIsNotNullPredicates: eq: {}", eq);
                return eq;
            }).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                var fname = f.getName();
                Path rootName = root.get(fname);
                log.debug("addIsNotNullPredicates: fieldName: {}, rootName: {}", fname, rootName);
                var predicate = criteriaBuilder.isNotNull(rootName);
                log.debug("addIsNotNullPredicates: predicate: {}", predicate);
                predicates.add(predicate);
            });
        }
    }

    public void addEqualsStringPredicates(Dto.Search search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.where().equalsString().entrySet()) {
            Arrays.stream(fields).filter(f -> {
                var fieldName = f.getName();
                log.debug("addEqualsStringPredicates: fieldName: {}, entry.key: {}", fieldName, entry.getKey());
                var eq = fieldName.equalsIgnoreCase(entry.getKey());
                log.debug("addEqualsStringPredicates: eq: {}", eq);
                return eq;
            }).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                var fieldName = f.getName();
                Path rootName = root.get(fieldName);
                var value = entry.getValue();
                log.debug("addEqualsStringPredicates: fieldName: {}, value: {}, rootName: {}", fieldName, value, rootName);
                var predicate = criteriaBuilder.equal(rootName, value);
                log.debug("addEqualsStringPredicates: predicate: {}", predicate);
                predicates.add(predicate);
            });
        }
    }

    private void addLikePredicates(Map<String, String> search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.entrySet()) {
            Arrays.stream(fields).filter(f -> {
                var fieldName = f.getName();
                log.debug("addLikePredicates: fieldName: {}, entry.key: {}", fieldName, entry.getKey());
                var eq = fieldName.equalsIgnoreCase(entry.getKey());
                log.debug("addLikePredicates: eq: {}", eq);
                return eq;
            }).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                var fieldName = f.getName();
                Path rootName = root.get(fieldName);
                var value = entry.getValue();
                log.debug("addLikePredicates: fieldName: {}, value: {}, rootName: {}", fieldName, value, rootName);
                var predicate = criteriaBuilder.like(criteriaBuilder.lower(rootName), ("%" + value.toLowerCase() + "%"));
                log.debug("addLikePredicates: predicate: {}", predicate);
                predicates.add(predicate);
            });
        }
    }

    private void addEqualsLongPredicates(Map<String, Long> search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.entrySet()) {
            Arrays.stream(fields).filter(f -> {
                var fieldName = f.getName();
                log.debug("addEqualsLongPredicates: fieldName: {}, entry.key: {}", fieldName, entry.getKey());
                var eq = fieldName.equalsIgnoreCase(entry.getKey());
                log.debug("addEqualsLongPredicates: eq: {}", eq);
                return eq;
            }).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                var fieldName = f.getName();
                Path rootName = root.get(fieldName);
                var value = entry.getValue();
                log.debug("addEqualsLongPredicates: fieldName: {}, value: {}, rootName: {}", fieldName, value, rootName);
                var predicate = criteriaBuilder.equal(rootName, value);
                log.debug("addEqualsLongPredicates: predicate: {}", predicate);
                predicates.add(predicate);
            });
        }
    }

    private boolean searchRecordValidation(Dto.Search models) {
        var whereIsPresent = models.where() != null;
        var projectionIsEmpty = models.projection() == null;
        var likeIsEmpty = whereIsPresent && models.where().like() != null;
        var equalsLongIsEmpty = whereIsPresent && models.where().equalsLong() == null;
        var isNullIsEmpty = whereIsPresent && models.where().isNull() == null;
        var isNotNullIsEmpty = whereIsPresent && models.where().isNotNull() == null;

        if (projectionIsEmpty && likeIsEmpty && equalsLongIsEmpty && isNullIsEmpty && isNotNullIsEmpty ) {
            throw new IllegalStateException("search.where().like(), search.where().equalsLong(), search.where().isNull(), search.isNotNullIsEmpty(), and search.projection are all empty, which is not supported");
        }
        return whereIsPresent;
    }


}
