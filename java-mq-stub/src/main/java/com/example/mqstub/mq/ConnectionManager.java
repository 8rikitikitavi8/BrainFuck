package com.example.mqstub.mq;

import com.example.mqstub.config.Config;
import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSContext;
import javax.jms.JMSException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final Map<String, MQConnectionFactory> factories = new HashMap<>();

    public ConnectionManager(Config.AppConfig appConfig) throws JMSException {
        // Configure per-broker factories
        for (Config.BrokerConfig b : appConfig.brokers) {
            MQConnectionFactory f = new MQConnectionFactory();
            f.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            f.setQueueManager(b.queueManager);
            f.setChannel(b.channel);
            f.setConnectionNameList(b.connectionName);

            if (b.tls != null && b.tls.enabled) {
                if (b.tls.cipher == null || b.tls.cipher.isEmpty()) {
                    throw new JMSException("TLS enabled for broker '" + b.name + "' but cipher not specified");
                }
                f.setSSLCipherSuite(b.tls.cipher);
                applyGlobalJSSE(b, appConfig);
            }

            factories.put(b.name, f);
        }
    }

    private static volatile JSSEProps appliedJsse;

    private static synchronized void applyGlobalJSSE(Config.BrokerConfig broker, Config.AppConfig appConfig) {
        Config.TLSConfig t = broker.tls;
        JSSEProps props = new JSSEProps(t.truststorePath, t.truststorePassword, t.keystorePath, t.keystorePassword);
        if (appliedJsse == null) {
            if (t.truststorePath != null) {
                System.setProperty("javax.net.ssl.trustStore", t.truststorePath);
            }
            if (t.truststorePassword != null) {
                System.setProperty("javax.net.ssl.trustStorePassword", t.truststorePassword);
            }
            if (t.keystorePath != null) {
                System.setProperty("javax.net.ssl.keyStore", t.keystorePath);
            }
            if (t.keystorePassword != null) {
                System.setProperty("javax.net.ssl.keyStorePassword", t.keystorePassword);
            }
            // Disable IBM cipher mappings if using Oracle/OpenJDK names
            System.setProperty("com.ibm.mq.cfg.useIBMCipherMappings", "false");
            appliedJsse = props;
            log.info("Applied JSSE truststore/keystore settings from broker {}", broker.name);
        } else if (!appliedJsse.equals(props)) {
            log.warn("Multiple TLS brokers specify different JSSE stores. Using the first applied. Broker: {}", broker.name);
        }
    }

    private static class JSSEProps {
        final String ts;
        final String tsp;
        final String ks;
        final String ksp;
        JSSEProps(String ts, String tsp, String ks, String ksp) {
            this.ts = ts; this.tsp = tsp; this.ks = ks; this.ksp = ksp;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JSSEProps)) return false;
            JSSEProps that = (JSSEProps) o;
            return Objects.equals(ts, that.ts) && Objects.equals(tsp, that.tsp)
                    && Objects.equals(ks, that.ks) && Objects.equals(ksp, that.ksp);
        }
        @Override public int hashCode() { return Objects.hash(ts, tsp, ks, ksp); }
    }

    public JMSContext createContext(String brokerName, String user, String password) throws JMSException {
        MQConnectionFactory f = factories.get(brokerName);
        if (f == null) throw new JMSException("Unknown broker: " + brokerName);
        if (user != null && !user.isEmpty()) {
            return f.createContext(user, password, JMSContext.CLIENT_ACKNOWLEDGE);
        }
        return f.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
    }

    public void close() {
        // MQConnectionFactory doesn't require explicit close; contexts are closed by workers
    }
}