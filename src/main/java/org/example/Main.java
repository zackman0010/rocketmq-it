package org.example;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
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


    public static void main(String[] args) throws Exception {
        final Main main = new Main();

        IntegrationTestBase.initTopic("aa", main.nsAddr, main.clusterName);
        final ClientServiceProvider provider = ClientServiceProvider.loadService();
        String endpoint = "localhost:8099";
        final ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder().setEndpoints(endpoint).build();
        final Producer producer =
            provider.newProducerBuilder().setClientConfiguration(clientConfiguration).setTopics("aa").build();
        final Message message = provider.newMessageBuilder().setTopic("aa").setBody("hello".getBytes()).build();
        final SendReceipt sendReceipt = producer.send(message);
        System.out.println(sendReceipt);
        System.out.println("xx");
        main.shutdown();
        System.out.println("done");
    }
}