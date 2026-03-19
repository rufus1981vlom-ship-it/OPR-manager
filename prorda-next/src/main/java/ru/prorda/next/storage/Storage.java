package ru.prorda.next.storage;

import ru.prorda.next.model.PublicationStatus;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Storage {
    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "piaro.db");
    }

    public synchronized void init() {
        tryConnectWithRecovery();
        initSchema();
    }

    private void tryConnectWithRecovery() {
        try {
            connect();
        } catch (SQLException first) {
            if (isCorrupt(first)) {
                recoverCorruptDb(first);
                try { connect(); } catch (SQLException second) { throw new RuntimeException(second); }
            } else throw new RuntimeException(first);
        }
    }

    private void connect() throws SQLException {
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
        }
    }

    private boolean isCorrupt(SQLException ex) {
        String m = (ex.getMessage() == null ? "" : ex.getMessage()).toLowerCase();
        return m.contains("database disk image is malformed") || m.contains("sqlite_corrupt") || ex.getErrorCode() == 11;
    }

    private void recoverCorruptDb(SQLException ex) {
        try {
            plugin.getLogger().warning("[Storage] SQLITE_CORRUPT detected: " + ex.getMessage());
            if (dbFile.exists()) {
                String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
                File backup = new File(plugin.getDataFolder(), "piaro.db.corrupt-" + ts + ".bak");
                Files.move(dbFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("[Storage] corrupted DB moved to: " + backup.getName());
            }
        } catch (Exception moveEx) {
            throw new RuntimeException(moveEx);
        }
    }

    private void initSchema() {
        execute("""
                CREATE TABLE IF NOT EXISTS post_history (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  rubric TEXT, reason_source TEXT, text TEXT, image_path TEXT,
                  status TEXT, reject_reason TEXT, context_fingerprint TEXT,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");
        execute("CREATE TABLE IF NOT EXISTS prompt_log (id INTEGER PRIMARY KEY AUTOINCREMENT, rubric TEXT, prompt TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        execute("CREATE TABLE IF NOT EXISTS openai_response_log (id INTEGER PRIMARY KEY AUTOINCREMENT, rubric TEXT, reason TEXT, status TEXT, raw_response TEXT, extracted_text TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        execute("CREATE TABLE IF NOT EXISTS publication_status (id INTEGER PRIMARY KEY AUTOINCREMENT, rubric TEXT, status TEXT, details TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        execute("CREATE TABLE IF NOT EXISTS topic_history (id INTEGER PRIMARY KEY AUTOINCREMENT, topic TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        execute("CREATE TABLE IF NOT EXISTS kv_state (key TEXT PRIMARY KEY, value TEXT)");
    }

    private void execute(String sql) {
        try (Statement st = connection.createStatement()) { st.execute(sql); } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public synchronized void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
        }
    }

    public synchronized void logPrompt(String rubric, String prompt) { ins("INSERT INTO prompt_log(rubric,prompt) VALUES(?,?)", rubric, prompt); }
    public synchronized void logOpenAi(String rubric, String reason, String status, String raw, String extracted) {
        ins("INSERT INTO openai_response_log(rubric,reason,status,raw_response,extracted_text) VALUES(?,?,?,?,?)", rubric, reason, status, raw, extracted);
    }

    public synchronized void logPublicationStatus(String rubric, PublicationStatus status, String details) {
        ins("INSERT INTO publication_status(rubric,status,details) VALUES(?,?,?)", rubric, status.code(), details);
        setState("debug.last_status", status.code());
        setState("debug.last_error", details == null ? "" : details);
    }

    public synchronized void logPostHistory(String rubric, String reason, String text, String status, String rejectReason, String fingerprint) {
        ins("INSERT INTO post_history(rubric,reason_source,text,status,reject_reason,context_fingerprint) VALUES(?,?,?,?,?,?)",
                rubric, reason, text, status, rejectReason, fingerprint);
    }

    private void ins(String sql, Object... values) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public synchronized void setState(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO kv_state(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public synchronized String getState(String key, String def) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM kv_state WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : def; }
        } catch (SQLException e) { return def; }
    }

    public synchronized int todayPublishedCount() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM post_history WHERE status='published' AND date(created_at)=date('now','localtime')")) {
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { return 0; }
    }

    public synchronized List<String> recentFingerprints(int limit) {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT context_fingerprint FROM post_history ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getString(1)); }
        } catch (SQLException ignored) {}
        return out;
    }

    public synchronized boolean topicUsedRecently(String topic, int days) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM topic_history WHERE topic=? AND datetime(created_at) > datetime('now', ? )")) {
            ps.setString(1, topic);
            ps.setString(2, "-" + Math.max(days, 1) + " days");
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
        } catch (SQLException e) { return false; }
    }

    public synchronized void logTopic(String topic) { ins("INSERT INTO topic_history(topic) VALUES(?)", topic); }
}
