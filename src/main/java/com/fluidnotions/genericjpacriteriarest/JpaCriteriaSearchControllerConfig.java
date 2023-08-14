package com.fluidnotions.genericjpacriteriarest;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@AutoConfigureAfter({WebMvcAutoConfiguration.class})
@ComponentScan(basePackageClasses = JpaCriteriaSearchController.class)
public class JpaCriteriaSearchControllerConfig {

}