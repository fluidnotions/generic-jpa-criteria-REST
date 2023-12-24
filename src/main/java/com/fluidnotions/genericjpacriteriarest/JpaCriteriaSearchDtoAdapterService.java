package com.fluidnotions.genericjpacriteriarest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class JpaCriteriaSearchDtoAdapterService {

    private final JpaCriteriaSearchService jpaCriteriaSearchService;

    public Dto.SearchResult searchWithEntityTypeResult(String entityName, Object search) {
        return jpaCriteriaSearchService.search(entityName, convert(search));
    }

    public List<?> search(String entityName, Object search) {
        return searchWithEntityTypeResult(entityName, search).results();
    }

    private Dto.Search convert(Object dto) {
        Field[] fields = dto.getClass().getDeclaredFields();
        Map<String, String> like = new HashMap<>();
        Map<String, Long> equalsLong = new HashMap<>();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            try {
                switch (fieldType.getSimpleName()) {
                    case "String":
                        String stringValue = (String) field.get(dto);
                        like.put(fieldName, stringValue);
                        break;
                    case "long":
                    case "Long":
                        Long longValue = (Long) field.get(dto);
                        equalsLong.put(fieldName, longValue);
                        break;
                    default:
                        log.error(fieldName + ": Unknown field type");
                        break;
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                log.error("convert pojo (dto) to Dto.Search failed", e);
            }
        }
        var builder = Dto.Where.builder();
        if (!like.isEmpty()) {
            builder.like(like);
        }
        if (!equalsLong.isEmpty()) {
            builder.equalsLong(equalsLong);
        }
        return Dto.Search.builder().where(builder.build()).build();
    }
}
