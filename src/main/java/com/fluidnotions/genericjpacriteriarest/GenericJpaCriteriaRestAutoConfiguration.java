package com.fluidnotions.genericjpacriteriarest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;


@Configuration
@AutoConfigureAfter({EntityManagerFactory.class, Jackson2ObjectMapperBuilder.class})
public class GenericJpaCriteriaRestAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean(value = ObjectMapper.class)
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder jacksonBuilder) {
        return jacksonBuilder.build();
    }

    @Bean
    @ConditionalOnMissingBean(value = EntityManager.class)
    public EntityManager entityManager(EntityManagerFactory entityManagerFactory) {
        return entityManagerFactory.createEntityManager();
    }

    @Bean
    @ConditionalOnClass(OpenAPI.class)
    public GroupedOpenApi libraryOpenApi() {
        String packagesToScan = getClass().getPackage().getName();
        return GroupedOpenApi.builder().group("REST API").packagesToScan(packagesToScan).build();
    }

    @Bean
    public JpaCriteriaSearchService jpaCriteriaSearchService(EntityManager entityManager, Jackson2ObjectMapperBuilder jacksonBuilder) {
        return new JpaCriteriaSearchService(entityManager, jacksonBuilder);
    }

    @Bean
    public JpaPatchController jpaPatchController(EntityManager entityManager) {
        return new JpaPatchController(entityManager);
    }

    @Bean
    public JpaCriteriaSearchController jpaCriteriaSearchController(JpaCriteriaSearchService jpaCriteriaSearchService) {
        return new JpaCriteriaSearchController(jpaCriteriaSearchService);
    }

    @Bean
    public JpaCriteriaSearchDtoAdapterService jpaCriteriaSearchDtoAdapterService(JpaCriteriaSearchService jpaCriteriaSearchService) {
        return new JpaCriteriaSearchDtoAdapterService(jpaCriteriaSearchService);
    }

}

