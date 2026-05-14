package org.ost.sqlengine.writer;

@SuppressWarnings("java:S2326") // E is load-bearing for type-safety at SqlEntityWriter<E> usage sites
public sealed interface SqlWriteColumn<E>
        permits SqlMappedColumn, SqlExpressionColumn {}
