package com.utilityresolver.main;

import com.utilityresolver.service.AppContext;
import com.utilityresolver.web.WebServer;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point. Starts the local web server and opens the app in the browser.
 *
 * Usage: java -jar UtilityDisputeResolver.jar [port] [--no-browser]
 * Data is stored in ./data and reports are written to ./reports next to where you run it.
 */
public class App {

    public static void main(String[] args) {
        int port = 8080;
        boolean openBrowser = true;
        for (String a : args) {
            if (a.equals("--no-browser")) {
                openBrowser = false;
            } else if (a.matches("\\d+")) {
                port = Integer.parseInt(a);
            }
        }
        try {
            Path dataDir = Paths.get("data");
            Path reportsDir = Paths.get("reports");
            AppContext ctx = new AppContext(dataDir, reportsDir);
            WebServer server = new WebServer(ctx);
            int actual = server.start(port);
            String url = "http://localhost:" + actual + "/";
            System.out.println("==============================================================");
            System.out.println(" Shared Apartment Utility & Dispute Resolver is running");
            System.out.println(" Open:    " + url);
            System.out.println(" Data:    " + dataDir.toAbsolutePath());
            System.out.println(" Reports: " + reportsDir.toAbsolutePath());
            System.out.println(" Press Ctrl+C to stop.");
            System.out.println("==============================================================");
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            if (openBrowser) {
                openInBrowser(url);
            }
        } catch (Exception e) {
            System.err.println("Could not start the application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception | Error e) {
            System.out.println("Open " + url + " in your browser.");
        }
    }
}
