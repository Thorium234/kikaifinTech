package com.schaccs.config.db;

import com.schaccs.repository.Database;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatasourceManager {

    private static final DatasourceManager INSTANCE = new DatasourceManager();

    private HikariDataSource remoteDataSource;
    private volatile boolean online = false;
    private volatile boolean fallbackToLocal = false;

    private DatasourceManager() {}

    public static DatasourceManager getInstance() { return INSTANCE; }

    public synchronized boolean connectRemote(DbConfig config) {
        try {
            if (remoteDataSource != null && !remoteDataSource.isClosed()) {
                remoteDataSource.close();
            }
            HikariConfig hikariConfig = new HikariConfig();
            String jdbcUrl = buildJdbcUrl(config);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(5000);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setMaxLifetime(600000);
            if ("postgresql".equalsIgnoreCase(config.getDbType())) {
                hikariConfig.setConnectionTestQuery("SELECT 1");
            }
            hikariConfig.addDataSourceProperty("ssl", Boolean.toString(
                    config.getSslMode() != null && config.getSslMode().equalsIgnoreCase("require")));
            remoteDataSource = new HikariDataSource(hikariConfig);
            try (Connection conn = remoteDataSource.getConnection()) {
                online = true;
                fallbackToLocal = false;
            }
            return true;
        } catch (Exception e) {
            online = false;
            fallbackToLocal = true;
            return false;
        }
    }

    public synchronized Connection getRemoteConnection() throws SQLException {
        if (remoteDataSource != null && !remoteDataSource.isClosed()) {
            try {
                Connection conn = remoteDataSource.getConnection();
                online = true;
                fallbackToLocal = false;
                return conn;
            } catch (SQLException e) {
                online = false;
                fallbackToLocal = true;
                throw e;
            }
        }
        online = false;
        fallbackToLocal = true;
        throw new SQLException("Remote datasource not configured or closed.");
    }

    public Connection getActiveConnection() throws SQLException {
        if (online && remoteDataSource != null) {
            try {
                return remoteDataSource.getConnection();
            } catch (SQLException e) {
                online = false;
                fallbackToLocal = true;
            }
        }
        return Database.getInstance().getConnection();
    }

    public boolean isOnline() { return online; }
    public boolean isFallbackToLocal() { return fallbackToLocal; }

    public synchronized void disconnectRemote() {
        if (remoteDataSource != null && !remoteDataSource.isClosed()) {
            remoteDataSource.close();
        }
        online = false;
        fallbackToLocal = true;
    }

    public void shutdown() {
        disconnectRemote();
    }

    private String buildJdbcUrl(DbConfig config) {
        if (config.getJdbcUrl() != null && !config.getJdbcUrl().isBlank()) {
            return config.getJdbcUrl();
        }
        String type = config.getDbType().toLowerCase();
        return switch (type) {
            case "postgresql" ->
                    "jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName();
            case "mysql" ->
                    "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName()
                            + "?useSSL=" + (config.getSslMode() != null && config.getSslMode().equalsIgnoreCase("require"))
                            + "&serverTimezone=UTC";
            case "mariadb" ->
                    "jdbc:mariadb://" + config.getHost() + ":" + config.getPort() + "/" + config.getDatabaseName()
                            + "?useSSL=" + (config.getSslMode() != null && config.getSslMode().equalsIgnoreCase("require"));
            default -> throw new IllegalArgumentException("Unsupported database type: " + type);
        };
    }

    public static final class DbConfig {
        private String jdbcUrl;
        private String dbType;
        private String host;
        private int port;
        private String databaseName;
        private String username;
        private String password;
        private String sslMode;
        private boolean active;

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSslMode() { return sslMode; }
        public void setSslMode(String sslMode) { this.sslMode = sslMode; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}
