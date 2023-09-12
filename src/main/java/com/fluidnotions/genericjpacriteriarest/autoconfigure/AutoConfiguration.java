package com.fluidnotions.genericjpacriteriarest.autoconfigure;

import com.fluidnotions.genericjpacriteriarest.JpaCriteriaSearchController;
import com.fluidnotions.genericjpacriteriarest.JpaCriteriaSearchService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@AutoConfigureAfter({WebMvcAutoConfiguration.class})
@ConditionalOnBean(EntityManagerFactory.class)
@ComponentScan(basePackageClasses = {JpaCriteriaSearchController.class, JpaCriteriaSearchService.class})
public class AutoConfiguration {

}
