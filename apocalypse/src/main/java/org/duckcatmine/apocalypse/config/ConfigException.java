package org.duckcatmine.apocalypse.config;

import java.util.List;

public final class ConfigException extends Exception {
    private final List<String> errors;

    public ConfigException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
