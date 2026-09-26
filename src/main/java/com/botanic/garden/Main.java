package com.botanic.garden;

import com.botanic.garden.http.WebServer;
import com.botanic.garden.store.DataStore;

import java.nio.file.Path;

/** 植物园游线规划器 —— 启动入口。 */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        String adminToken = env("ADMIN_TOKEN", "admin123");
        Path dataFile = Path.of(env("DATA_FILE", "data/garden.json"));
        Path staticRoot = Path.of(env("STATIC_ROOT", "src/main/resources/static")).toAbsolutePath().normalize();

        DataStore store = new DataStore(dataFile);
        store.load();

        new WebServer(store, adminToken, staticRoot).start(port);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
