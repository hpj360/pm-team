package com.redteam.parse.config;

import lombok.Data;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Kafka 与异步任务配置
 *
 * <p>声明解析事件专用 KafkaTemplate 与解析任务线程池。</p>
 *
 * @author 红方团队
 */
@Data
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "redteam.kafka")
public class KafkaConfig {

    /**
     * Kafka bootstrap servers
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * 主题配置
     */
    private Topic topic = new Topic();

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

        /**
         * 解析事件主题
         */
        private String parseEvents = "redteam.parse.events";

        /**
         * 文件解析完成主题（V4.7-P0-3 新增：触发 ai-service 生成威胁摘要）
         */
        private String fileParsed = "file.parsed";
    }

    /**
     * 解析事件 ProducerFactory
     *
     * @return ProducerFactory
     */
    @Bean("parseEventProducerFactory")
    public ProducerFactory<String, String> parseEventProducerFactory() {
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
     * 解析事件 KafkaTemplate
     *
     * @return KafkaTemplate
     */
    @Bean("parseEventKafkaTemplate")
    public KafkaTemplate<String, String> parseEventKafkaTemplate() {
        return new KafkaTemplate<>(parseEventProducerFactory());
    }

    /**
     * 解析任务线程池（供 @Async 使用）
     *
     * @return 线程池
     */
    @Bean("parseTaskExecutor")
    public Executor parseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("parse-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
