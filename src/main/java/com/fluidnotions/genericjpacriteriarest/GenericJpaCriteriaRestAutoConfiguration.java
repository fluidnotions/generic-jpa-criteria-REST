package com.fluidnotions.genericjpacriteriarest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
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
    @ConditionalOnMissingBean(value = JpaCriteriaSearchService.class)
    public JpaCriteriaSearchService jpaCriteriaSearchService(EntityManager entityManager, Jackson2ObjectMapperBuilder jacksonBuilder) {
        return new JpaCriteriaSearchService(entityManager, jacksonBuilder);
    }

    @Bean
    @ConditionalOnMissingBean(value = JpaCriteriaSearchController.class)
    public JpaCriteriaSearchController jpaCriteriaSearchController(JpaCriteriaSearchService jpaCriteriaSearchService) {
        return new JpaCriteriaSearchController(jpaCriteriaSearchService);
    }

}

