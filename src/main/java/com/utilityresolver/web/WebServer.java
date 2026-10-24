package com.utilityresolver.web;

import com.sun.net.httpserver.HttpServer;
import com.utilityresolver.service.AppContext;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Embedded HTTP server built on the JDK's com.sun.net.httpserver (no external libraries). */
public class WebServer {
    private final AppContext ctx;
    private HttpServer server;
    private int port;

    public WebServer(AppContext ctx) {
        this.ctx = ctx;
    }

    /** Starts on the requested port, or the next free one if it is busy. */
    public int start(int preferredPort) throws IOException {
        IOException last = null;
        for (int p = preferredPort; p < preferredPort + 20; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                port = p;
                break;
            } catch (BindException e) {
                last = e;
            }
        }
        if (server == null) {
            throw last != null ? last : new IOException("No free port found");
        }
        server.createContext("/api/", new ApiHandler(ctx));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        return port;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return port;
    }
}
