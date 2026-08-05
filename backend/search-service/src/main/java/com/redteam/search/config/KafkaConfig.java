package com.redteam.search.config;

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
 * <p>声明检索事件专用 KafkaTemplate 与索引任务线程池。
 * 检索服务消费 {@code redteam.parse.events}，并向 {@code redteam.search.events} 投递检索行为事件。</p>
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
         * 解析事件主题（消费）
         */
        private String parseEvents = "redteam.parse.events";

        /**
         * 检索事件主题（生产）
         */
        private String searchEvents = "redteam.search.events";
    }

    /**
     * 检索事件 ProducerFactory
     *
     * @return ProducerFactory
     */
    @Bean("searchEventProducerFactory")
    public ProducerFactory<String, String> searchEventProducerFactory() {
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
     * 检索事件 KafkaTemplate
     *
     * @return KafkaTemplate
     */
    @Bean("searchEventKafkaTemplate")
    public KafkaTemplate<String, String> searchEventKafkaTemplate() {
        return new KafkaTemplate<>(searchEventProducerFactory());
    }

    /**
     * 索引任务线程池（供 @Async 使用）
     *
     * @return 线程池
     */
    @Bean("searchIndexExecutor")
    public Executor searchIndexExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("search-index-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
