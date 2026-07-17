package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;

public interface SchemaMigration {

    int version();

    String description();

    default String checksum() {
        return Integer.toHexString((version() + ":" + description() + ":" + getClass().getName()).hashCode());
    }

    void apply(Connection connection) throws SQLException;
}
