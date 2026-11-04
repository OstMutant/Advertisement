package org.ost.restapi.api.patch;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies an RFC 7396 merge patch to a write record and validates the result. */
@Component
@RequiredArgsConstructor
public class MergePatchSupport {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Validator validator;

    /**
     * Returns {@code current} with {@code patch} merged over it.
     *
     * @throws IllegalArgumentException    when the patch is not an object, names an unknown member or holds a value of the wrong type
     * @throws PatchValidationException when the merged record violates its validation constraints
     */
    public <T extends Record> T apply(@NonNull T current, @NonNull JsonNode patch, @NonNull Class<T> type) {
        if (!patch.isObject()) {
            throw new IllegalArgumentException("A merge patch must be a JSON object");
        }
        Set<String> known = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        patch.propertyNames().forEach(name -> {
            if (!known.contains(name)) {
                throw new IllegalArgumentException("Unknown field: " + name);
            }
        });

        JsonNode merged = merge(MAPPER.valueToTree(current), patch);
        T result;
        try {
            result = MAPPER.treeToValue(merged, type);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("A patch member has a value of the wrong type", ex);
        }

        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            violations.forEach(v -> fieldErrors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
            throw new PatchValidationException(fieldErrors);
        }
        return result;
    }

    private static JsonNode merge(JsonNode target, JsonNode patch) {
        if (!patch.isObject()) {
            return patch;
        }
        ObjectNode result = target.isObject() ? ((ObjectNode) target).deepCopy() : MAPPER.createObjectNode();
        patch.properties().forEach(entry -> {
            if (entry.getValue().isNull()) {
                result.remove(entry.getKey());
            } else {
                result.set(entry.getKey(), merge(result.get(entry.getKey()) == null ? MAPPER.createObjectNode() : result.get(entry.getKey()), entry.getValue()));
            }
        });
        return result;
    }
}
