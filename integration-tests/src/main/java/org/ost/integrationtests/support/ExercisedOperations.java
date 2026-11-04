package org.ost.integrationtests.support;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe record of the REST requests Level 3 scenarios sent (with the response statuses seen) and of the scenario classes that ran, read by the operation coverage test. */
@UtilityClass
public class ExercisedOperations {

    private final Set<String> OPERATIONS = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<Integer>> STATUSES = new ConcurrentHashMap<>();
    private final Set<Class<?>> SCENARIO_CLASSES = ConcurrentHashMap.newKeySet();

    public String key(@NonNull String httpMethod, @NonNull String pathPattern) {
        return httpMethod + " " + pathPattern;
    }

    public void recordRequest(@NonNull String httpMethod, @NonNull String pathPattern, int status) {
        String key = key(httpMethod, pathPattern);
        OPERATIONS.add(key);
        STATUSES.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(status);
    }

    public void recordScenarioClass(@NonNull Class<?> scenarioClass) {
        SCENARIO_CLASSES.add(scenarioClass);
    }

    public Set<String> requests() {
        return Set.copyOf(OPERATIONS);
    }

    public Map<String, Set<Integer>> statuses() {
        Map<String, Set<Integer>> copy = new TreeMap<>();
        STATUSES.forEach((key, seen) -> copy.put(key, new TreeSet<>(seen)));
        return copy;
    }

    public Set<Class<?>> scenarioClasses() {
        return Set.copyOf(SCENARIO_CLASSES);
    }
}
