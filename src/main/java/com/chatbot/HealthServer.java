package com.chatbot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.sun.net.httpserver.HttpServer;

/**
 * HTTP server tối giản để Render (Web Service) / UptimeRobot ping giữ bot không ngủ.
 */
public class HealthServer {

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String body;
            if ("/health".equals(exchange.getRequestURI().getPath())) {
                body = "{\"status\":\"ok\",\"timestamp\":\"" + Instant.now() + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
            } else {
                body = "OK";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("🩺 Health server chạy ở cổng " + port);
    }
}
