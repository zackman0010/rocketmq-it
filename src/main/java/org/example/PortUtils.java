package org.example;

public class NetworkUtils {
    private NetworkUtils() {
    }

    public static int getBrokerPort() {
        String port = System.getenv("rocketmq.broker.port");
        if (null == port) {
            port = "10911";
        }
        return Integer.parseInt(port);
    }

    public static int getProxyPort() {
        String port = System.getenv("rocketmq.proxy.port");
        if (null == port) {
            port = "8081";
        }
        return Integer.parseInt(port);
    }

    public static int getBrokerHAServicePort() {
        String port = System.getenv("rocketmq.broker.ha.port");
        if (null == port) {
            port = "10912";
        }
        return Integer.parseInt(port);
    }

    public static int getNamesrvPort() {
        String port = System.getenv("rocketmq.namesrv.port");
        if (null == port) {
            port = "9876";
        }
        return Integer.parseInt(port);
    }

    public static String getServerIp() {
        String ip = System.getenv("rocketmq.proxy.ip");
        if (null == ip) {
            ip = "127.0.0.1";
        }
        return ip;
    }
}
