package com.example.mqstub;

import com.example.mqstub.config.Config;
import com.example.mqstub.mq.ConnectionManager;
import com.example.mqstub.route.RouteWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String configPath = null;
        boolean dryRun = false;
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[i + 1];
                i++;
            } else if ("--dry-run".equals(args[i])) {
                dryRun = true;
            }
        }
        if (configPath == null) {
            System.err.println("Usage: java -jar mq-stub.jar --config /path/config.yaml [--dry-run]");
            System.exit(2);
            return;
        }

        Config.AppConfig appConfig = Config.load(configPath);
        log.info("Loaded {} brokers and {} routes", appConfig.brokers.size(), appConfig.routes.size());

        if (dryRun) {
            for (Config.RouteConfig r : appConfig.routes) {
                log.info("Route: {}/{} -> {}/{}", r.source.broker, r.source.queue, r.target.broker, r.target.queue);
            }
            return;
        }

        ConnectionManager connectionManager = new ConnectionManager(appConfig);

        List<RouteWorker> workers = new ArrayList<>();
        for (Config.RouteConfig route : appConfig.routes) {
            RouteWorker w = new RouteWorker(route, connectionManager);
            workers.add(w);
            w.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Stopping...");
            for (RouteWorker w : workers) {
                w.requestStop();
            }
            for (RouteWorker w : workers) {
                try {
                    w.join(5000);
                } catch (InterruptedException ignored) {}
            }
            connectionManager.close();
            log.info("Stopped");
        }));

        for (RouteWorker w : workers) {
            w.join();
        }
    }
}