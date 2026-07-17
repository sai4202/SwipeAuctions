package com.swipeauctions;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal status endpoint so the running skeleton has something to show in a browser.
 * Read-only health check only — no schema access (schema is owned by Flyway migrations).
 */
@RestController
public class StatusController {

    private final JdbcTemplate jdbc;

    public StatusController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "SwipeAuctions");
        body.put("status", "UP");
        body.put("time", OffsetDateTime.now().toString());

        Map<String, Object> db = new LinkedHashMap<>();
        try {
            Integer one = jdbc.queryForObject("select 1", Integer.class);
            String version = jdbc.queryForObject("select version()", String.class);
            Integer tableCount = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_schema = 'public'",
                    Integer.class);
            db.put("connected", one != null && one == 1);
            db.put("server", version);
            db.put("publicTables", tableCount);
        } catch (Exception e) {
            db.put("connected", false);
            db.put("error", e.getMessage());
        }
        body.put("database", db);
        return body;
    }
}
