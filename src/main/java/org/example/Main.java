package org.example;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

public class Main {
    public static final String nsAddr;
    public static final String broker1Addr;
    static final String broker1Name;
    static final String clusterName;
    static final NamesrvController namesrvController;
    static final BrokerController brokerController;

    static {
        System.setProperty(
            RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        namesrvController = IntegrationTestBase.createAndStartNamesrv();
        nsAddr = "localhost:" + namesrvController.getNettyServerConfig().getListenPort();
        brokerController = IntegrationTestBase.createAndStartBroker(nsAddr);
        IntegrationTestBase.createAndStartProxy(brokerController);
        clusterName = brokerController.getBrokerConfig().getBrokerClusterName();
        broker1Name = brokerController.getBrokerConfig().getBrokerName();
        broker1Addr = "localhost:" + brokerController.getNettyServerConfig().getListenPort();
    }


    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}