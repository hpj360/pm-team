package com.redteam.feishu.config;

import com.lark.oa.sdk.Lark;
import com.lark.oa.sdk.LarkConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "feishu")
public class FeishuConfig {

    private String appId;
    private String appSecret;
    private String encryptKey;
    private String verificationToken;

    @Bean
    public Lark larkClient() {
        LarkConfiguration config = LarkConfiguration.builder()
                .appId(appId)
                .appSecret(appSecret)
                .build();
        return new Lark(config);
    }
}
