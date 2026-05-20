package com.tasktracker.config.converter;

import com.tasktracker.model.TaskStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class TaskStatusConverter implements Converter<String, TaskStatus> {

    @Override
    public TaskStatus convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado no puede ser nulo o vacío.");
        }
        try {
            return TaskStatus.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de tarea no válido: '" + source + "'. Los valores permitidos son: " + Arrays.toString(TaskStatus.values()));
        }
    }
}