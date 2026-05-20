package com.tasktracker.config.converter;

import com.tasktracker.model.TaskPriority;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class TaskPriorityConverter implements Converter<String, TaskPriority> {

    @Override
    public TaskPriority convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("La prioridad no puede ser nula o vacía.");
        }
        try {
            return TaskPriority.valueOf(source.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Prioridad de tarea no válida: '" + source + "'. Los valores permitidos son: " + Arrays.toString(TaskPriority.values()));
        }
    }
}