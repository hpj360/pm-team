package com.redteam.upload.config;

import lombok.Data;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置类
 *
 * <p>显式声明文件事件 ProducerFactory 与 KafkaTemplate，统一管理事件投递。
 * 默认使用 StringSerializer（事件体由 FileEventProducer 序列化为 JSON 字符串发送），
 * 这样上游投递的 JSON 字符串与下游 parse-service / search-service / notification-service
 * 已有的 String 反序列化保持一致。</p>
 *
 * @author 红方团队
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "redteam.kafka")
public class KafkaConfig {

    /**
     * Kafka 主题配置
     */
    private Topic topic = new Topic();

    /**
     * Kafka bootstrap servers（默认与 spring.kafka.bootstrap-servers 一致）
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * 主题配置
     *
     * @author 红方团队
     */
    @Data
    public static class Topic {
        /**
         * 文件事件主题
         */
        private String fileEvents = "redteam.file.events";
    }

    /**
     * 文件事件 ProducerFactory
     *
     * <p>ack=all 保证可靠性；启用幂等避免重复消息。</p>
     *
     * @return ProducerFactory
     */
    @Bean("fileEventProducerFactory")
    public ProducerFactory<String, String> fileEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * 文件事件 KafkaTemplate
     *
     * <p>当容器中已存在默认 KafkaTemplate 时，使用 @Bean(name=...) 区分以避免冲突；
     * FileEventProducer 通过构造注入默认 KafkaTemplate 即可使用统一配置。</p>
     *
     * @return KafkaTemplate
     */
    @Bean("fileEventKafkaTemplate")
    public KafkaTemplate<String, String> fileEventKafkaTemplate() {
        return new KafkaTemplate<>(fileEventProducerFactory());
    }
}
