package com.redteam.search.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Milvus 向量数据库配置类
 *
 * <p>创建 MilvusServiceClient 单例 Bean，连接参数从 {@link SearchProperties.Milvus} 读取。
 * 销毁时关闭客户端以释放 gRPC 通道。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MilvusConfig {

    private final SearchProperties searchProperties;

    private MilvusServiceClient milvusServiceClient;

    /**
     * 创建 Milvus 客户端
     *
     * @return MilvusServiceClient
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        SearchProperties.Milvus conf = searchProperties.getMilvus();
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(conf.getHost())
                .withPort(conf.getPort())
                .withConnectTimeout(5L, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTime(60L, java.util.concurrent.TimeUnit.SECONDS)
                // .withKeepAliveWithoutCalls(false) // 临时注释：SDK 版本兼容问题
                .build();
        milvusServiceClient = new MilvusServiceClient(connectParam);
        log.info("Milvus 客户端已创建: host={}, port={}, collection={}",
                conf.getHost(), conf.getPort(), conf.getCollectionName());
        return milvusServiceClient;
    }

    /**
     * 容器销毁时关闭 Milvus 客户端
     */
    @PreDestroy
    public void destroy() {
        if (milvusServiceClient != null) {
            try {
                milvusServiceClient.close();
                log.info("Milvus 客户端已关闭");
            } catch (Exception e) { // 临时改为 Exception：SDK 版本兼容问题
                log.warn("关闭 Milvus 客户端异常", e);
            }
        }
    }
}
