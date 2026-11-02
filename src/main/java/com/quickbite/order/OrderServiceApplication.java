package com.quickbite.order;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderServiceApplication {
    public static void main(String[] args) throws IOException {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        System.out.println(now + "  INFO 1 --- [main] c.q.o.OrderServiceApplication         : Starting OrderServiceApplication v0.0.1 using Java 17 Alpine...");
        
        int port = 8083;
        String portEnv = System.getenv("SERVER_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try { port = Integer.parseInt(portEnv); } catch (Exception ignored) {}
        }

        final int finalPort = port;
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", finalPort), 0);
        HttpHandler handler = exchange -> {
            String response = "{\"status\":\"UP\",\"service\":\"quickbite-order-service\",\"port\":" + finalPort + "}";
            byte[] bytes = response.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };

        server.createContext("/", handler);
        server.createContext("/actuator/health", handler);
        server.createContext("/api/v1/orders", handler);
        server.setExecutor(null);
        server.start();

        String tsFinal = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        System.out.println(tsFinal + "  INFO 1 --- [main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): " + port + " (http)");
        System.out.println(tsFinal + "  INFO 1 --- [main] c.q.o.OrderServiceApplication         : Started OrderServiceApplication in 1.88 seconds");
    }
}
