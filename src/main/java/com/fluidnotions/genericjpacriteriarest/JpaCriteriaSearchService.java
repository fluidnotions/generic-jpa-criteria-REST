package com.fluidnotions.genericjpacriteriarest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@Service
@SuppressWarnings("unchecked")
public class JpaCriteriaSearchService {

    private final EntityManager entityManager;
    private final Jackson2ObjectMapperBuilder jacksonBuilder;
    @Value("${rest-jpa-criteria-search.entity-name-fallback-prefix:none}")
    private String entityNameFallbackPrefix;

    private ObjectMapper objectMapper() {

        @JsonIgnoreProperties({"hibernateLazyInitializer"})
        record HibernateMixin() {
        }

        jacksonBuilder.mixIn(Object.class, HibernateMixin.class);
        ObjectMapper objectMapper = jacksonBuilder.build();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return objectMapper;
    }

    public Dto.SearchResult search(String entityName, Dto.Search search) {
        var entities = entityManager.getMetamodel().getEntities();
        var entityNames = entities.stream().map(e -> e.getName().toLowerCase()).collect(Collectors.joining(", "));
        log.debug("Target entity: {}, All entities: {}", entityName, entityNames);
        EntityType<?> entityType = getEntityType(entityName, entities);

        if (entityType != null) {
            log.debug("Entity: {}", entityType);
            var results = searchEntities(entityType.getJavaType(), entityManager, search);
            return Dto.SearchResult.builder().results(results).entityType(entityType).build();
        }else if (!entityNameFallbackPrefix.equals("none")) {
            entityType = getEntityType(entityNameFallbackPrefix + entityName, entities);
            if (entityType != null) {
                log.debug("Entity: {}", entityType);
                var results = searchEntities(entityType.getJavaType(), entityManager, search);
                return Dto.SearchResult.builder().results(results).entityType(entityType).build();
            }
        }
        throw new RuntimeException("No entity type '%s' found".formatted(entityName));
    }

    public String searchAndSerialize(String entityName, Dto.Search search) {
        var result = search(entityName, search);
        if (result.results() != null && result.results().size() > 0) {
            try {
                return serializeEntityTypeList(result.results(), result.entityType(), search);
            } catch (JsonProcessingException e) {
                log.error("Error serializing results", e);
                return "[]";
            }
        }
        else {
            return "[]";
        }
    }

    private EntityType<?> getEntityType(String entity, Set<EntityType<?>> entities) {
        var entityType = entities.stream().filter(e -> e.getName().equalsIgnoreCase(entity)).findFirst().orElse(null);
        return entityType;
    }

    /**
     * Using multiSelect didn't work on all entities for unknown reasons, so we have to fetch everything and manually filter the results.
     * An alternative would be to use a DTO projection, dynamically generated, but that would require a lot of reflection and would be more complex.
     * */
    private String serializeEntityTypeList(List<?> results, EntityType<?> entityType, Dto.Search search) throws JsonProcessingException {
        var objectMapper = objectMapper();

        if (search.projection() != null && search.projection().length > 0) {
            SimpleModule module = new SimpleModule();
            module.addSerializer(entityType.getJavaType(), new JsonSerializer<Object>() {
                @Override
                public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    ObjectMapper defaultMapper = objectMapper();
                    ObjectNode objectNode = defaultMapper.valueToTree(value);

                    ObjectNode filteredNode = defaultMapper.createObjectNode();
                    for (String path : search.projection()) {
                        String[] pathSegments = path.contains(".") ? path.split("\\.") : new String[]{path};
                        buildFilteredNode(pathSegments, objectNode, filteredNode);
                    }
                    gen.writeTree(filteredNode);
                }

                private static void buildFilteredNode(String[] pathSegments, ObjectNode objectNode, ObjectNode filteredNode) {
                    ObjectNode currentNode = objectNode;
                    for (String segment : pathSegments) {
                        if (currentNode.has(segment)) {
                            currentNode = buildFilteredNodeSegment(filteredNode, segment, currentNode);
                            if (currentNode == null) break;
                        }
                        else if (currentNode.has(segment.toLowerCase())) {
                            currentNode = buildFilteredNodeSegment(filteredNode, segment.toLowerCase(), currentNode);
                            if (currentNode == null) break;
                        }
                        else {
                            break;
                        }
                    }
                }

                private static ObjectNode buildFilteredNodeSegment(ObjectNode filteredNode, String segment, ObjectNode currentNode) {
                    JsonNode childNode = currentNode.get(segment);
                    if (childNode.isObject()) {
                        currentNode = (ObjectNode) childNode;
                    }
                    else {
                        filteredNode.set(segment, childNode);
                        return null;
                    }
                    return currentNode;
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
        if (whereIsPresent && search.where().like() != null && search.where().like().values().stream().allMatch(value -> value != null)) {
            addLikePredicates(search, criteriaBuilder, root, predicates, fields);
        }
        if (whereIsPresent && search.where().equalsLong() != null && search.where().equalsLong().values().stream().allMatch(value -> value != null)) {
            addEqualsLongPredicates(search, criteriaBuilder, root, predicates, fields);
        }
        if (whereIsPresent && search.where().notEqualsLong() != null && search.where().notEqualsLong().values().stream().allMatch(value -> value != null)) {
            addNotEqualsLongPredicates(search, criteriaBuilder, root, predicates, fields);
        }
        if (whereIsPresent && search.where().isNotNull() != null && search.where().isNotNull().stream().allMatch(value -> value != null)) {
            addIsNotNullPredicates(search, fields, root, criteriaBuilder, predicates);
        }
        if (whereIsPresent && search.where().isNull() != null && search.where().isNull().stream().allMatch(value -> value != null)) {
            addIsNullPredicates(search, fields, root, criteriaBuilder, predicates);
        }
        if (whereIsPresent && search.where().equalsString() != null  && search.where().equalsString().values().stream().allMatch(value -> value != null)) {
            addEqualsStringPredicates(search, criteriaBuilder, root, predicates, fields);
        }
        if (predicates.size() > 0) {
            criteriaQuery.where(predicates.toArray(new Predicate[predicates.size()]));
        }
        var query = entityManager.createQuery(criteriaQuery);
        //compensate for db load hit from fake projection in serializeEntityTypeList
        query.setHint("jakarta.persistence.cache.storeMode", CacheStoreMode.USE);
        query.setHint("jakarta.persistence.cache.retrieveMode", CacheRetrieveMode.USE);
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

    private void addLikePredicates(Dto.Search search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.where().like().entrySet()) {
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

    private void addEqualsLongPredicates(Dto.Search search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.where().equalsLong().entrySet()) {
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

    private void addNotEqualsLongPredicates(Dto.Search search, CriteriaBuilder criteriaBuilder, Root<?> root, List<Predicate> predicates, Field[] fields) {
        for (var entry : search.where().notEqualsLong().entrySet()) {
            Arrays.stream(fields).filter(f -> {
                var fieldName = f.getName();
                log.debug("addNotEqualsLongPredicates: fieldName: {}, entry.key: {}", fieldName, entry.getKey());
                var eq = fieldName.equalsIgnoreCase(entry.getKey());
                log.debug("addEqualsLongPredicates: eq: {}", eq);
                return eq;
            }).findFirst().ifPresent(f -> {
                f.setAccessible(true);
                var fieldName = f.getName();
                Path rootName = root.get(fieldName);
                var value = entry.getValue();
                log.debug("addNotEqualsLongPredicates: fieldName: {}, value: {}, rootName: {}", fieldName, value, rootName);
                var predicate = criteriaBuilder.notEqual(rootName, value);
                log.debug("addNotEqualsLongPredicates: predicate: {}", predicate);
                predicates.add(predicate);
            });
        }
    }

    private boolean searchRecordValidation(Dto.Search models) {
        var where = models.where();
        var whereIsPresent = where != null;
        var projectionIsEmpty = models.projection() == null || models.projection().length == 0;
        var likeIsEmpty = whereIsPresent && (where.like() == null || where.like().isEmpty());
        var equalsLongIsEmpty = whereIsPresent && (where.equalsLong() == null || where.equalsLong().isEmpty());
        var notEqualsLongIsEmpty = whereIsPresent && (where.notEqualsLong() == null || where.notEqualsLong().isEmpty());
        var isNullIsEmpty = whereIsPresent && (where.isNull() == null || where.isNull().isEmpty());
        var isNotNullIsEmpty = whereIsPresent && (where.isNotNull() == null || where.isNotNull().isEmpty());

        if (likeIsEmpty && equalsLongIsEmpty && isNullIsEmpty && isNotNullIsEmpty && projectionIsEmpty && notEqualsLongIsEmpty) {
            throw new IllegalStateException("search.where().like(), search.where().equalsLong(), search.where().isNull(), search.isNotNullIsEmpty(), and search.projection are all null or empty, which is not supported");
        }
        return whereIsPresent;
    }


}
