package org.example;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.proxy.grpc.v2.GrpcMessagingApplication;
import org.apache.rocketmq.proxy.processor.MessagingProcessor;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

public class Main {
    public final String nsAddr;
    public final String broker1Addr;
    final String broker1Name;
    final String clusterName;
    final NamesrvController namesrvController;
    final BrokerController brokerController;

    final MessagingProcessor messagingProcessor;

    final GrpcMessagingApplication grpcMessagingApplication;

    public Main() {
        System.setProperty(
            RemotingCommand.REMOTING_VERSION_KEY, Integer.toString(MQVersion.CURRENT_VERSION));
        namesrvController = IntegrationTestBase.createAndStartNamesrv();
        nsAddr = "localhost:" + namesrvController.getNettyServerConfig().getListenPort();
        brokerController = IntegrationTestBase.createAndStartBroker(nsAddr);
        messagingProcessor = IntegrationTestBase.createAndStartMessagingProcessor(brokerController);
        grpcMessagingApplication = IntegrationTestBase.createAndStartProxy(messagingProcessor);
        clusterName = brokerController.getBrokerConfig().getBrokerClusterName();
        broker1Name = brokerController.getBrokerConfig().getBrokerName();
        broker1Addr = "localhost:" + brokerController.getNettyServerConfig().getListenPort();
    }

    public void shutdown() throws Exception {
        messagingProcessor.shutdown();
        grpcMessagingApplication.shutdown();
        brokerController.shutdown();
        namesrvController.shutdown();
    }

    public static void main(String[] args) {
        final Main main = new Main();
        IntegrationTestBase.initTopic("aa", main.nsAddr, main.clusterName);
    }
}