FROM eclipse-temurin:8

LABEL MAINTAINER="zackman0010"
LABEL ORIGINAL_MAINTAINER = "aiyangkun"

COPY target/rocketmq-proxy-it.jar /root/rocketmq-proxy-it.jar
COPY src/main/resources/rmq-proxy-home/conf/rmq-proxy.json /root/rmq-proxy.json

CMD ["java", "-jar", "/root/rocketmq-proxy-it.jar"]