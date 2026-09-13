package kr.itsdev.auth.common.oauth;

import java.util.Map;

final class OAuth2ProfileAttributes {
    private OAuth2ProfileAttributes() {
    }

    static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static Boolean firstBoolean(Map<String, Object> attributes, String... keys) {
        for (String key : keys) {
            if (attributes.containsKey(key)) {
                Object value = attributes.get(key);
                return value instanceof Boolean booleanValue
                        ? booleanValue
                        : Boolean.valueOf(String.valueOf(value));
            }
        }
        return null;
    }
}
