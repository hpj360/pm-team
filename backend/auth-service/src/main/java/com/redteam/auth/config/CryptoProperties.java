package com.redteam.auth.config;

import com.redteam.common.util.crypto.Sm2Util;
import com.redteam.common.util.crypto.Sm4Util;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * 国密算法密钥配置
 *
 * <p>集中管理 SM2 密钥对与 SM4 对称密钥，供 auth-service 各服务类注入使用。
 * 启动时若未配置密钥，将自动生成临时密钥并打印警告日志（仅适用于开发/测试环境）。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "redteam.crypto")
public class CryptoProperties {

    /**
     * SM4 对称密钥（Base64 编码，16 字节）
     */
    private String sm4Key;

    /**
     * SM2 私钥（Base64 PKCS#8 编码）
     */
    private String sm2PrivateKey;

    /**
     * SM2 公钥（Base64 X.509 编码）
     */
    private String sm2PublicKey;

    /**
     * 启动时校验并初始化密钥
     *
     * <p>若密钥未配置，则自动生成临时密钥（仅开发环境使用，生产环境必须配置）。</p>
     */
    @PostConstruct
    public void init() {
        if (sm4Key == null || sm4Key.isBlank()) {
            this.sm4Key = Sm4Util.generateKey();
            log.warn("【安全警告】未配置 redteam.crypto.sm4-key，已生成临时 SM4 密钥，重启后敏感数据将无法解密！生产环境必须配置固定密钥。");
        }
        if ((sm2PrivateKey == null || sm2PrivateKey.isBlank())
                || (sm2PublicKey == null || sm2PublicKey.isBlank())) {
            String[] keyPair = Sm2Util.generateKeyPairBase64();
            this.sm2PublicKey = keyPair[0];
            this.sm2PrivateKey = keyPair[1];
            log.warn("【安全警告】未配置 redteam.crypto.sm2-private-key / sm2-public-key，已生成临时 SM2 密钥对，重启后 SM2 Token 将失效！生产环境必须配置固定密钥。");
        }
        log.info("国密密钥初始化完成（SM4 + SM2 已就绪）");
    }

    /**
     * 获取 SM4 密钥的校验结果
     *
     * @return SM4 密钥是否已就绪
     */
    public boolean isSm4Ready() {
        return Objects.nonNull(sm4Key) && !sm4Key.isBlank();
    }

    /**
     * 获取 SM2 密钥对的校验结果
     *
     * @return SM2 密钥对是否已就绪
     */
    public boolean isSm2Ready() {
        return Objects.nonNull(sm2PrivateKey) && !sm2PrivateKey.isBlank()
                && Objects.nonNull(sm2PublicKey) && !sm2PublicKey.isBlank();
    }
}
