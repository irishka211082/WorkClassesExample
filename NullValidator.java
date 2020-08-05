package com.davita.cwow.patient.spanner.util;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAdjuster;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NullValidator {

    private static final List<Class<?>> primitives = Arrays.asList(
            Number.class,
            Date.class,
            Boolean.class,
            String.class,
            TemporalAdjuster.class,
            UUID.class
    );

    public static boolean deepNonNullCheck(Object object) {
        if (Objects.isNull(object)) {
            return false;
        }
        if (isPrimitive(object)) {
            return true;
        }
        if (object instanceof Collection) {
            return ((Collection<?>) object).stream().anyMatch(NullValidator::deepNonNullCheck);
        }
        if (object instanceof Map) {
            return ((Map<?, ?>) object).values().stream().anyMatch(NullValidator::deepNonNullCheck);
        }

        List<Field> fieldsWithGetters = getFieldsWithGetters(
                getObjectFields(object.getClass(), new ArrayList<>()),
                getObjectMethods(object.getClass(), new ArrayList<>())
        );

        fieldsWithGetters.forEach(field -> field.setAccessible(true));

        return getValuesFromGetters(object, fieldsWithGetters).stream()
                .filter(Objects::nonNull)
                .anyMatch(NullValidator::deepNonNullCheck);
    }

    private static boolean isPrimitive(Object object) {
        return primitives.stream().anyMatch(primitiveClass -> primitiveClass.isAssignableFrom(object.getClass()));
    }

    private static List<Field> getObjectFields(Class<?> clazz, List<Field> fields) {
        fields.addAll(Arrays.asList(clazz.getDeclaredFields()));

        if (Objects.nonNull(clazz.getSuperclass())) {
            getObjectFields(clazz.getSuperclass(), fields);
        }

        return fields;
    }

    private static List<Method> getObjectMethods(Class<?> clazz, List<Method> methods) {
        methods.addAll(Arrays.asList(clazz.getMethods()));

        if (Objects.nonNull(clazz.getSuperclass())) {
            getObjectMethods(clazz.getSuperclass(), methods);
        }

        return methods;
    }

    private static List<Field> getFieldsWithGetters(
            List<Field> fields,
            List<Method> methods) {
        List<String> methodsAsString = methods.stream()
                .map(Method::getName)
                .filter(methodName -> methodName.startsWith("get"))
                .collect(Collectors.toList());

        return fields.stream()
                .filter(field -> methodsAsString.contains(getFieldGetterName(field)))
                .collect(Collectors.toList());
    }

    private static String getFieldGetterName(Field field) {
        return "get" + StringUtils.capitalize(field.getName());
    }

    private static List<Object> getValuesFromGetters(Object object, List<Field> fields) {
        return fields.stream()
                .map((ThrowingFunction<Field, Object>) field -> field.get(object))
                .collect(Collectors.toList());
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> extends Function<T, R> {
        @Override
        default R apply(T t) {
            try {
                return applyWithThrows(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        R applyWithThrows(T t) throws Exception;
    }
}
