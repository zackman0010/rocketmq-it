package org.example;

import static java.util.Collections.emptyMap;
import static org.apache.rocketmq.proxy.config.ConfigurationManager.RMQ_PROXY_HOME;

import apache.rocketmq.v2.MessagingServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.BrokerConfig;
import org.apache.rocketmq.common.namesrv.NamesrvConfig;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.apache.rocketmq.proxy.config.ConfigurationManager;
import org.apache.rocketmq.proxy.grpc.interceptor.ContextInterceptor;
import org.apache.rocketmq.proxy.grpc.interceptor.HeaderInterceptor;
import org.apache.rocketmq.proxy.grpc.v2.GrpcMessagingApplication;
import org.apache.rocketmq.proxy.processor.DefaultMessagingProcessor;
import org.apache.rocketmq.proxy.processor.MessagingProcessor;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyServerConfig;
import org.apache.rocketmq.store.config.MessageStoreConfig;
import org.junit.Assert;


public class IntegrationTestBase {
    public static final InternalLogger logger =
        InternalLoggerFactory.getLogger(IntegrationTestBase.class);

    static final String BROKER_NAME_PREFIX = "TestBrokerName_";
    static final AtomicInteger BROKER_INDEX = new AtomicInteger(0);
    static final List<File> TMPE_FILES = new ArrayList<>();
    static final List<BrokerController> BROKER_CONTROLLERS = new ArrayList<>();
    static final List<NamesrvController> NAMESRV_CONTROLLERS = new ArrayList<>();
    static final int COMMIT_LOG_SIZE = 1024 * 1024 * 100;
    static final int INDEX_NUM = 1000;

    //    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private static String createTempDir() {
        String path = null;
        try {
            File file = Files.createTempDirectory("opentelemetry-rocketmq-client-temp").toFile();
            TMPE_FILES.add(file);
            path = file.getCanonicalPath();
        } catch (IOException e) {
            logger.warn("Error creating temporary directory.", e);
        }
        return path;
    }

    public static void deleteTempDir() {
        for (File file : TMPE_FILES) {
            boolean deleted = file.delete();
            if (!deleted) {
                file.deleteOnExit();
            }
        }
    }

    public static NamesrvController createAndStartNamesrv() {
        String baseDir = createTempDir();
        Path kvConfigPath = Paths.get(baseDir, "namesrv", "kvConfig.json");
        Path namesrvPath = Paths.get(baseDir, "namesrv", "namesrv.properties");

        NamesrvConfig namesrvConfig = new NamesrvConfig();
        NettyServerConfig nameServerNettyServerConfig = new NettyServerConfig();

        namesrvConfig.setKvConfigPath(kvConfigPath.toString());
        namesrvConfig.setConfigStorePath(namesrvPath.toString());

        // find 3 consecutive open ports and use the last one of them
        // rocketmq will also bind to given port - 2
        nameServerNettyServerConfig.setListenPort(PortUtils.findOpenPorts(3) + 2);
        NamesrvController namesrvController =
            new NamesrvController(namesrvConfig, nameServerNettyServerConfig);
        try {
            Assert.assertTrue(namesrvController.initialize());
            logger.info("Name Server Start:{}", nameServerNettyServerConfig.getListenPort());
            namesrvController.start();
        } catch (Exception e) {
            logger.info("Name Server start failed", e);
        }
        NAMESRV_CONTROLLERS.add(namesrvController);
        return namesrvController;
    }

    public static BrokerController createAndStartBroker(String nsAddr) {
        String baseDir = createTempDir();
        Path commitLogPath = Paths.get(baseDir, "commitlog");

        BrokerConfig brokerConfig = new BrokerConfig();
        MessageStoreConfig storeConfig = new MessageStoreConfig();
        brokerConfig.setBrokerName(BROKER_NAME_PREFIX + BROKER_INDEX.getAndIncrement());
        brokerConfig.setBrokerIP1("127.0.0.1");
        brokerConfig.setNamesrvAddr(nsAddr);
        brokerConfig.setEnablePropertyFilter(true);
        storeConfig.setStorePathRootDir(baseDir);
        storeConfig.setStorePathCommitLog(commitLogPath.toString());
        storeConfig.setMappedFileSizeCommitLog(COMMIT_LOG_SIZE);
        storeConfig.setMaxIndexNum(INDEX_NUM);
        storeConfig.setMaxHashSlotNum(INDEX_NUM * 4);
        return createAndStartBroker(storeConfig, brokerConfig);
    }

    protected static void setUpServer(MessagingServiceGrpc.MessagingServiceImplBase serverImpl,
        int port, boolean enableInterceptor) throws IOException, CertificateException {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        ServerServiceDefinition serviceDefinition = ServerInterceptors.intercept(serverImpl);
        if (enableInterceptor) {
            serviceDefinition = ServerInterceptors.intercept(serverImpl, new ContextInterceptor(), new HeaderInterceptor());
        }
        Server server = NettyServerBuilder.forPort(port)
            .directExecutor()
            .addService(serviceDefinition)
            .useTransportSecurity(selfSignedCertificate.certificate(), selfSignedCertificate.privateKey())
            .build()
            .start();
        final int port1 = server.getPort();
        // Create a server, add service, start, and register for automatic graceful shutdown.
        //        grpcCleanup.register(server);

        ConfigurationManager.getProxyConfig().setGrpcServerPort(port1);
    }

    public static BrokerController createAndStartBroker(
        MessageStoreConfig storeConfig, BrokerConfig brokerConfig) {
        NettyServerConfig nettyServerConfig = new NettyServerConfig();
        NettyClientConfig nettyClientConfig = new NettyClientConfig();
        nettyServerConfig.setListenPort(PortUtils.findOpenPort());
        storeConfig.setHaListenPort(PortUtils.findOpenPort());
        BrokerController brokerController =
            new BrokerController(brokerConfig, nettyServerConfig, nettyClientConfig, storeConfig);
        try {
            Assert.assertTrue(brokerController.initialize());
            logger.info(
                "Broker Start name:{} addr:{}",
                brokerConfig.getBrokerName(),
                brokerController.getBrokerAddr());
            brokerController.start();
        } catch (Throwable t) {
            logger.error("Broker start failed", t);
            throw new IllegalStateException("Broker start failed", t);
        }
        BROKER_CONTROLLERS.add(brokerController);
        return brokerController;
    }

    public static void createAndStartProxy(BrokerController brokerController) {
        try {
            String mockProxyHome = "/mock/rmq/proxy/home";
            URL mockProxyHomeURL = IntegrationTestBase.class.getClassLoader().getResource("rmq-proxy-home");
            if (mockProxyHomeURL != null) {
                mockProxyHome = mockProxyHomeURL.toURI().getPath();
            }

            if (null != mockProxyHome) {
                System.setProperty(RMQ_PROXY_HOME, mockProxyHome);
            }

            ConfigurationManager.initEnv();
            ConfigurationManager.intConfig();
            final BrokerConfig brokerConfig = brokerController.getBrokerConfig();
            ConfigurationManager.getProxyConfig().setNamesrvAddr(brokerConfig.getNamesrvAddr());
            // Set LongPollingReserveTimeInMillis to 500ms to reserve more time for IT
            ConfigurationManager.getProxyConfig().setLongPollingReserveTimeInMillis(500);
            ConfigurationManager.getProxyConfig().setRocketMQClusterName(brokerConfig.getBrokerClusterName());
            ConfigurationManager.getProxyConfig().setMinInvisibleTimeMillsForRecv(3);

            MessagingProcessor messagingProcessor = DefaultMessagingProcessor.createForLocalMode(brokerController);
            messagingProcessor.start();
            GrpcMessagingApplication grpcMessagingApplication = GrpcMessagingApplication.create(messagingProcessor);
            grpcMessagingApplication.start();
            setUpServer(grpcMessagingApplication, ConfigurationManager.getProxyConfig().getGrpcServerPort(), true);
        } catch (Throwable t) {
            logger.error("Proxy start failed", t);
            throw new IllegalStateException("Proxy start failed", t);
        }
    }

    public static void initTopic(String topic, String nsAddr, String clusterName) {
        try {
            // RocketMQ 4.x
            Class<?> mqAdmin = Class.forName("org.apache.rocketmq.test.util.MQAdmin");
            Method createTopic =
                mqAdmin.getMethod("createTopic", String.class, String.class, String.class, int.class);
            createTopic.invoke(null, nsAddr, clusterName, topic, 20);
        } catch (ClassNotFoundException
                 | InvocationTargetException
                 | NoSuchMethodException
                 | IllegalAccessException e) {

            // RocketMQ 5.x
            try {
                Class<?> mqAdmin = Class.forName("org.apache.rocketmq.test.util.MQAdminTestUtils");
                Method createTopic =
                    mqAdmin.getMethod(
                        "createTopic", String.class, String.class, String.class, int.class, Map.class);
                createTopic.invoke(null, nsAddr, clusterName, topic, 20, emptyMap());
            } catch (ClassNotFoundException
                     | InvocationTargetException
                     | NoSuchMethodException
                     | IllegalAccessException ex) {
                throw new LinkageError("Could not initialize topic", ex);
            }
        }
    }

    private IntegrationTestBase() {}
}
