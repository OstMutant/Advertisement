package org.ost.sqlengine.exec;

@FunctionalInterface
public interface SqlCommand {

    String sql();

    static SqlCommand of(String sql) { return () -> sql; }
}
