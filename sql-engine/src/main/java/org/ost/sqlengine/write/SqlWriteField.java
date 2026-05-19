package org.ost.sqlengine.write;

@SuppressWarnings("java:S2326") // E is load-bearing for type-safety at SqlEntityWriter<E> usage sites
public sealed interface SqlWriteField<E>
        permits SqlMappedField, SqlExpressionField {}
