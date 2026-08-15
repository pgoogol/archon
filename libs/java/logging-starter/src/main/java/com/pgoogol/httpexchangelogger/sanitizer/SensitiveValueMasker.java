package com.pgoogol.httpexchangelogger.sanitizer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class SensitiveValueMasker {

    public static final String MASK = "***";

    private final Set<String> normalizedFields;

    public SensitiveValueMasker(List<String> fields) {

        this.normalizedFields = new HashSet<>();
        if (Objects.nonNull(fields)) {

            fields.stream() //
                    .filter(Objects::nonNull) //
                    .map(SensitiveValueMasker::normalize) //
                    .forEach(normalizedFields::add);
        }
    }

    private static String normalize(String name) {

        return name.toLowerCase(Locale.ROOT);
    }

    public boolean isSensitive(String name) {

        if (Objects.isNull(name)) {

            return false;
        }
        return normalizedFields.contains(normalize(name));
    }

    public String mask(String value) {

        if (Objects.isNull(value)) {

            return null;
        }
        return MASK;
    }

}
