package com.tasktracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.tasktracker.config.converter.TaskPriorityConverter;
import com.tasktracker.config.converter.TaskStatusConverter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new TaskStatusConverter());
        registry.addConverter(new TaskPriorityConverter());
    }
}