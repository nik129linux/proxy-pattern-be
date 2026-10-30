package com.proxypattern.service;

import com.proxypattern.error.InvalidParamsException;
import java.util.Map;

/** Typed reads from the operation body map. */
public final class Params {

    private Params() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> body(Object... params) {
        if (params == null || params.length == 0 || params[0] == null) {
            return Map.of();
        }
        if (params[0] instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new InvalidParamsException("body must be a JSON object");
    }

    public static String text(Map<String, Object> body, String key) {
        if (body.get(key) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new InvalidParamsException(key + " is required");
    }

    public static int positiveInt(Map<String, Object> body, String key) {
        int value = integer(body, key);
        if (value <= 0) {
            throw new InvalidParamsException(key + " must be positive");
        }
        return value;
    }

    public static double positiveDouble(Map<String, Object> body, String key) {
        if (!(body.get(key) instanceof Number number) || number.doubleValue() <= 0) {
            throw new InvalidParamsException(key + " must be a positive number");
        }
        return number.doubleValue();
    }

    private static int integer(Map<String, Object> body, String key) {
        if (!(body.get(key) instanceof Number number)) {
            throw new InvalidParamsException(key + " must be a number");
        }
        double value = number.doubleValue();
        if (value != Math.rint(value) || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new InvalidParamsException(key + " must be a whole number");
        }
        return (int) value;
    }
}
