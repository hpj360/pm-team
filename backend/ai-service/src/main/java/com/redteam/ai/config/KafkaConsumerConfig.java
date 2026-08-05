package com.redteam.ai.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 消费者配置（V4.7-P0-3）
 *
 * <p>配置统一的错误处理器，实现消费失败重试 + 死信队列：</p>
 * <ul>
 *   <li>重试策略：{@link FixedBackOff} 间隔 1 秒、重试 3 次（共 4 次尝试）</li>
 *   <li>死信队列：3 次重试仍失败后，将消息投递到 {@code <原主题>.dlq}
 *       （例如 {@code file.parsed} → {@code file.parsed.dlq}）</li>
 *   <li>死信后自动 ack，consumer 继续消费下一条消息，不阻塞</li>
 * </ul>
 *
 * @author 红方团队
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaConsumerConfig {

    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL_MS = 1000L;

    /**
     * 最大重试次数（不含首次尝试）
     */
    private static final long MAX_RETRIES = 3L;

    /**
     * 死信主题后缀
     */
    private static final String DLQ_SUFFIX = ".dlq";

    /**
     * 配置 Kafka 监听器错误处理器
     *
     * <p>消费失败后重试 3 次（间隔 1 秒），仍失败则投递到死信主题 {@code <原主题>.dlq}，
     * 随后 ack 该消息，保证 consumer 不被毒丸消息阻塞。</p>
     *
     * @param kafkaTemplate Kafka 模板（用于死信投递，由 Spring Boot 自动配置）
     * @return DefaultErrorHandler
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 死信发布器：将失败消息投递到 <原主题>.dlq
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    // 死信主题 = 原主题 + ".dlq"，分区沿用原分区（无效时回退 0）
                    int partition = record.partition() >= 0 ? record.partition() : 0;
                    return new TopicPartition(record.topic() + DLQ_SUFFIX, partition);
                }
        );
        // FixedBackOff(interval, maxAttempts)：间隔 1 秒，重试 3 次（不含首次），共 4 次尝试
        FixedBackOff backOff = new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // 死信投递后不再重试，提交 offset 继续消费
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
