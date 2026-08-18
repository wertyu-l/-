package com.example.demo.simulator.tlv.input.frontend;

import com.example.demo.common.Result;
import com.example.demo.simulator.tlv.input.core.SimDeviceManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 前端管理服务（JDK 内置 HttpServer）。
 * <p>
 * 与 UDP 业务端口共用同一端口号（TCP），提供静态管理页面与 JSON API，
 * 供浏览器查看/调试设备状态。返回结构复用 {@link Result}，与 REST 模拟器前端一致。
 */
@Component
public class FrontendServer {

    private final int port;
    private final SimDeviceManager manager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    public FrontendServer(@Value("${port:8090}") int port, SimDeviceManager manager) {
        this.port = port;
        this.manager = manager;
    }

    @PostConstruct
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::route);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
        } catch (IOException e) {
            System.err.println("[FrontendServer] 端口 " + port + " 启动失败: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            // 处理 CORS 预检请求
            if ("OPTIONS".equals(method)) {
                addCorsHeaders(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if ("PUT".equals(method) && path.startsWith("/simulator/channel/")) {
                String subPath = path.substring("/simulator/channel/".length());
                String channelName = subPath.substring(0, subPath.lastIndexOf('/'));
                channelName = URLDecoder.decode(channelName, StandardCharsets.UTF_8);
                String body = readBody(ex);
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(body, Map.class);
                String sourceUrl = (String) map.get("sourceUrl");
                manager.setChannelUrl(channelName, sourceUrl);
                writeJson(ex, 200, Result.success(null));
                return;
            }
            if (!"GET".equals(method)) {
                writeJson(ex, 404, Result.error("不支持的方法"));
                return;
            }
            switch (path) {
                case "/":
                case "/index.html":
                    serveStatic(ex);
                    return;
                case "/simulator/device/info":
                    writeJson(ex, 200, Result.success(manager.getDeviceInfo()));
                    return;
                case "/simulator/device/status":
                    writeJson(ex, 200, Result.success(manager.getDeviceStatus()));
                    return;
                case "/simulator/device/capability":
                    writeJson(ex, 200, Result.success(manager.getDeviceCapability()));
                    return;
                case "/simulator/device/windows":
                    writeJson(ex, 200, Result.success(manager.getWindows()));
                    return;
                case "/simulator/channel/urls":
                    writeJson(ex, 200, Result.success(manager.getChannelUrls()));
                    return;
                default:
                    writeJson(ex, 404, Result.error("未找到: " + path));
            }
        } finally {
            ex.close();
        }
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/index.html")) {
            if (in == null) {
                writeJson(ex, 404, Result.error("页面不存在"));
                return;
            }
            byte[] bytes = in.readAllBytes();
            addCorsHeaders(ex);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        }
    }

    private void writeJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(obj);
        addCorsHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    private void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, PUT, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

}