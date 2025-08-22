package com.example.mqstub.route;

import com.example.mqstub.config.Config;
import com.example.mqstub.mq.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;

public class RouteWorker extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RouteWorker.class);

    private final Config.RouteConfig route;
    private final ConnectionManager connectionManager;
    private volatile boolean stop;

    public RouteWorker(Config.RouteConfig route, ConnectionManager connectionManager) {
        super("route-" + route.source.broker + "." + route.source.queue + "-to-" + route.target.broker + "." + route.target.queue);
        setDaemon(true);
        this.route = route;
        this.connectionManager = connectionManager;
    }

    public void requestStop() {
        stop = true;
    }

    @Override
    public void run() {
        log.info("Starting route: {}/{} -> {}/{}", route.source.broker, route.source.queue, route.target.broker, route.target.queue);
        JMSContext srcCtx = null;
        JMSContext dstCtx = null;
        JMSConsumer consumer = null;
        JMSProducer producer = null;
        try {
            srcCtx = connectionManager.createContext(route.source.broker, route.source.user, route.source.password);
            dstCtx = connectionManager.createContext(route.target.broker, route.target.user, route.target.password);

            Queue srcQueue = srcCtx.createQueue("queue:///" + route.source.queue);
            Queue dstQueue = dstCtx.createQueue("queue:///" + route.target.queue);

            consumer = srcCtx.createConsumer(srcQueue);
            producer = dstCtx.createProducer();

            while (!stop) {
                Message m = consumer.receive(route.waitIntervalMs);
                if (m == null) {
                    try { Thread.sleep(route.idleSleepMs); } catch (InterruptedException ignored) {}
                    continue;
                }

                try {
                    if (m instanceof BytesMessage) {
                        BytesMessage bm = (BytesMessage) m;
                        long len = bm.getBodyLength();
                        byte[] payload = new byte[(int) len];
                        bm.readBytes(payload);
                        BytesMessage out = dstCtx.createBytesMessage();
                        out.writeBytes(payload);
                        producer.send(dstQueue, out);
                    } else if (m instanceof TextMessage) {
                        String text = ((TextMessage) m).getText();
                        producer.send(dstQueue, text);
                    } else {
                        // Fallback: try getBody
                        byte[] payload = m.getBody(byte[].class);
                        BytesMessage out = dstCtx.createBytesMessage();
                        out.writeBytes(payload);
                        producer.send(dstQueue, out);
                    }
                    m.acknowledge();
                } catch (Exception ex) {
                    log.error("Route processing error", ex);
                    // Nack is not available in CLIENT_ACK; message will be redelivered after session recover
                    srcCtx.recover();
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        } catch (Exception e) {
            log.error("Route fatal error", e);
        } finally {
            if (consumer != null) consumer.close();
            if (producer != null) producer.close();
            if (srcCtx != null) srcCtx.close();
            if (dstCtx != null) dstCtx.close();
            log.info("Route stopped: {}/{} -> {}/{}", route.source.broker, route.source.queue, route.target.broker, route.target.queue);
        }
    }
}