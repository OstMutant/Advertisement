package org.ost.sqlengine.writer;

public sealed interface SqlWriteColumn<E>
        permits SqlColumnDefinition, SqlExpressionColumn {}
