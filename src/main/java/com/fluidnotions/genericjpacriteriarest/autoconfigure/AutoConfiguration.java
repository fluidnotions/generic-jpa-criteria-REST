package com.fluidnotions.genericjpacriteriarest.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluidnotions.genericjpacriteriarest.JpaCriteriaSearchController;
import com.fluidnotions.genericjpacriteriarest.JpaCriteriaSearchService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;


@Configuration
@AutoConfigureAfter({WebMvcAutoConfiguration.class})
@ConditionalOnBean(EntityManagerFactory.class)
@ComponentScan(basePackageClasses = {JpaCriteriaSearchController.class, JpaCriteriaSearchService.class})
public class AutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        return builder;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
