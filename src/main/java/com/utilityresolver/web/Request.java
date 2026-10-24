package com.utilityresolver.web;

import com.sun.net.httpserver.HttpExchange;
import com.utilityresolver.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Convenience wrapper around HttpExchange: path parts, query params and JSON body helpers. */
public class Request {
    private final HttpExchange exchange;
    private final String[] parts;
    private final Map<String, String> query = new HashMap<>();
    private Map<String, Object> body;

    public Request(HttpExchange exchange) {
        this.exchange = exchange;
        String path = exchange.getRequestURI().getPath();
        List<String> segs = new ArrayList<>();
        for (String s : path.split("/")) {
            if (!s.isEmpty()) {
                segs.add(URLDecoder.decode(s, StandardCharsets.UTF_8));
            }
        }
        this.parts = segs.toArray(new String[0]);
        String q = exchange.getRequestURI().getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    query.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
    }

    public String method() {
        return exchange.getRequestMethod().toUpperCase();
    }

    /** Path segment after "/api", e.g. for /api/bills/B3 -> part(0)="bills", part(1)="B3". */
    public String part(int i) {
        int idx = i + 1;
        return idx < parts.length ? parts[idx] : null;
    }

    public int partCount() {
        return Math.max(0, parts.length - 1);
    }

    public String query(String key) {
        return query.get(key);
    }

    public YearMonth monthParam() {
        String m = query("month");
        if (m == null || m.isBlank()) {
            return YearMonth.now();
        }
        return parseMonth(m);
    }

    public Map<String, Object> body() throws IOException {
        if (body == null) {
            try (InputStream in = exchange.getRequestBody()) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                try {
                    body = Json.parseObject(text);
                } catch (RuntimeException e) {
                    throw new HttpError(400, "Invalid JSON: " + e.getMessage());
                }
            }
        }
        return body;
    }

    public String str(String key) throws IOException {
        Object v = body().get(key);
        return v == null ? null : v.toString();
    }

    public double num(String key) throws IOException {
        Object v = body().get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v == null || v.toString().isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new HttpError(400, "'" + key + "' must be a number");
        }
    }

    public LocalDate date(String key) throws IOException {
        String s = str(key);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new HttpError(400, "'" + key + "' must be a date like 2026-10-24");
        }
    }

    public YearMonth month(String key) throws IOException {
        String s = str(key);
        if (s == null || s.isBlank()) {
            throw new HttpError(400, "'" + key + "' is required");
        }
        return parseMonth(s);
    }

    public static YearMonth parseMonth(String s) {
        try {
            return YearMonth.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new HttpError(400, "Month must look like 2026-10");
        }
    }

    public HttpExchange exchange() {
        return exchange;
    }
}
