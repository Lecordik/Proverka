package org.lex;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;


public class DatabaseManager {

    private static final Preferences PREFS = Preferences.userNodeForPackage(DatabaseManager.class);
    private static final String PREF_LOCAL_PATH    = "local_path";
    private static final String PREF_REMOTE_HOST   = "remote_host";
    private static final String PREF_REMOTE_DB     = "remote_db";
    private static final String PREF_REMOTE_USER   = "remote_user";
    private static final String PREF_REMOTE_PASS   = "remote_pass";
    private static final String PREF_REMOTE_TYPE   = "remote_type";
    private static final String PREF_REMOTE_ENABLE = "remote_enable";

    public enum DbType { POSTGRESQL, MYSQL }


    private Connection localConn;
    private String     currentLocalPath; // путь к файлу, открытому сегодня


    private Connection remoteConn;

    public DatabaseManager() {
        loadSqliteDriver();
        openLocalDb();
        if (isRemoteEnabled()) {
            try { openRemoteDb(); } catch (Exception e) {
                System.err.println("[DB] Удалённая БД недоступна: " + e.getMessage());
            }
        }
    }

    private void loadSqliteDriver() {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new RuntimeException("SQLite JDBC не найден", e); }
    }

    public void openLocalDb() {
        try {
            String folder = getLocalPath();
            File dir = new File(folder);
            if (!dir.exists()) dir.mkdirs();

            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String filePath = folder + File.separator + "kiz_" + today + ".db";

            if (filePath.equals(currentLocalPath) && localConn != null && !localConn.isClosed()) return;

            if (localConn != null && !localConn.isClosed()) localConn.close();

            localConn = DriverManager.getConnection("jdbc:sqlite:" + filePath);
            localConn.setAutoCommit(true);
            currentLocalPath = filePath;
            createLocalTable(localConn);
            System.out.println("[DB] Локальная БД: " + filePath);
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка открытия локальной БД: " + e.getMessage(), e);
        }
    }

    private void createLocalTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS scans (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    scanned_at  TEXT    NOT NULL,
                    raw_data    TEXT    NOT NULL,
                    gtin        TEXT,
                    serial      TEXT,
                    verify_key  TEXT,
                    verify_code TEXT,
                    is_valid    INTEGER NOT NULL DEFAULT 0,
                    error_msg   TEXT
                )
            """);
        }
    }

    public void openRemoteDb() throws Exception {
        String host = getRemoteHost();
        String db   = getRemoteDb();
        String user = getRemoteUser();
        String pass = getRemotePass();
        DbType type = getRemoteType();

        if (host.isBlank() || db.isBlank()) throw new Exception("Не заполнены параметры удалённой БД");

        String url;
        if (type == DbType.POSTGRESQL) {
            Class.forName("org.postgresql.Driver");
            url = "jdbc:postgresql://" + host + "/" + db;
        } else {
            Class.forName("com.mysql.cj.jdbc.Driver");
            url = "jdbc:mysql://" + host + "/" + db + "?useSSL=false&serverTimezone=UTC";
        }

        if (remoteConn != null && !remoteConn.isClosed()) remoteConn.close();
        remoteConn = DriverManager.getConnection(url, user, pass);
        remoteConn.setAutoCommit(true);
        createRemoteTable(remoteConn, type);
        System.out.println("[DB] Удалённая БД подключена: " + url);
    }

    private void createRemoteTable(Connection conn, DbType type) throws SQLException {
        String autoincrement = type == DbType.POSTGRESQL ? "SERIAL PRIMARY KEY" : "INT AUTO_INCREMENT PRIMARY KEY";
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS scans (
                    id          %s,
                    scanned_at  TIMESTAMP   NOT NULL,
                    raw_data    TEXT        NOT NULL,
                    gtin        VARCHAR(14),
                    serial      VARCHAR(20),
                    verify_key  VARCHAR(10),
                    verify_code TEXT,
                    is_valid    BOOLEAN     NOT NULL DEFAULT FALSE,
                    error_msg   TEXT
                )
            """.formatted(autoincrement));
        }
    }

    public String testRemoteConnection() {
        try {
            openRemoteDb();
            String info = remoteConn.getMetaData().getDatabaseProductName()
                    + " " + remoteConn.getMetaData().getDatabaseProductVersion();
            return "✓ Подключено: " + info;
        } catch (Exception e) {
            return "✗ Ошибка: " + e.getMessage();
        }
    }


    public SaveResult saveScan(String rawData, KizParser.ParseResult pr) {
        openLocalDb();

        String sql = """
            INSERT INTO scans (scanned_at, raw_data, gtin, serial, verify_key, verify_code, is_valid, error_msg)
            VALUES (datetime('now','localtime'), ?, ?, ?, ?, ?, ?, ?)
        """;

        boolean localOk  = false;
        boolean remoteOk = false;
        String  localErr = null;
        String  remoteErr= null;

        try (PreparedStatement ps = localConn.prepareStatement(sql)) {
            ps.setString(1, rawData);
            ps.setString(2, pr.gtin);
            ps.setString(3, pr.serial);
            ps.setString(4, pr.verifyKey);
            ps.setString(5, pr.verifyCode);
            ps.setInt   (6, pr.valid ? 1 : 0);
            ps.setString(7, pr.valid ? null : pr.errorMsg);
            ps.executeUpdate();
            localOk = true;
        } catch (SQLException e) {
            localErr = e.getMessage();
            System.err.println("[DB] Ошибка локальной записи: " + e.getMessage());
        }

        if (remoteConn != null) {
            String remoteSql = """
                INSERT INTO scans (scanned_at, raw_data, gtin, serial, verify_key, verify_code, is_valid, error_msg)
                VALUES (NOW(), ?, ?, ?, ?, ?, ?, ?)
            """;
            try {
                // Проверка живости соединения
                if (remoteConn.isClosed() || !remoteConn.isValid(2)) openRemoteDb();

                try (PreparedStatement ps = remoteConn.prepareStatement(remoteSql)) {
                    ps.setString(1, rawData);
                    ps.setString(2, pr.gtin);
                    ps.setString(3, pr.serial);
                    ps.setString(4, pr.verifyKey);
                    ps.setString(5, pr.verifyCode);
                    ps.setBoolean(6, pr.valid);
                    ps.setString(7, pr.valid ? null : pr.errorMsg);
                    ps.executeUpdate();
                    remoteOk = true;
                }
            } catch (Exception e) {
                remoteErr = e.getMessage();
                System.err.println("[DB] Ошибка удалённой записи: " + e.getMessage());
            }
        }

        return new SaveResult(localOk, remoteOk, localErr, remoteErr, currentLocalPath);
    }

    public void close() {
        try { if (localConn  != null && !localConn.isClosed())  localConn.close();  } catch (SQLException ignored) {}
        try { if (remoteConn != null && !remoteConn.isClosed()) remoteConn.close(); } catch (SQLException ignored) {}
    }

    public String getLocalPath() {
        return PREFS.get(PREF_LOCAL_PATH,
                System.getProperty("user.home") + File.separator + "KIZ_DB");
    }
    public void setLocalPath(String p) { PREFS.put(PREF_LOCAL_PATH, p); }

    public String getRemoteHost()   { return PREFS.get(PREF_REMOTE_HOST, ""); }
    public String getRemoteDb()     { return PREFS.get(PREF_REMOTE_DB, "kiz_db"); }
    public String getRemoteUser()   { return PREFS.get(PREF_REMOTE_USER, ""); }
    public String getRemotePass()   { return PREFS.get(PREF_REMOTE_PASS, ""); }
    public boolean isRemoteEnabled(){ return PREFS.getBoolean(PREF_REMOTE_ENABLE, false); }

    public DbType getRemoteType() {
        return "mysql".equals(PREFS.get(PREF_REMOTE_TYPE, "postgresql"))
                ? DbType.MYSQL : DbType.POSTGRESQL;
    }

    public void saveRemoteSettings(String host, String db, String user, String pass, DbType type) {
        PREFS.put(PREF_REMOTE_HOST, host);
        PREFS.put(PREF_REMOTE_DB, db);
        PREFS.put(PREF_REMOTE_USER, user);
        PREFS.put(PREF_REMOTE_PASS, pass);
        PREFS.put(PREF_REMOTE_TYPE, type == DbType.MYSQL ? "mysql" : "postgresql");
        PREFS.putBoolean(PREF_REMOTE_ENABLE, !host.isBlank());
    }

    public record SaveResult(
            boolean localOk,
            boolean remoteOk,
            String  localError,
            String  remoteError,
            String  localFilePath
    ) {
        public String toStatusString() {
            StringBuilder sb = new StringBuilder();
            sb.append(localOk ? "💾 local ✓" : "💾 local ✗");
            if (remoteOk)                    sb.append("  ☁ remote ✓");
            else if (remoteError != null)    sb.append("  ☁ remote ✗");
            return sb.toString();
        }
    }
}
