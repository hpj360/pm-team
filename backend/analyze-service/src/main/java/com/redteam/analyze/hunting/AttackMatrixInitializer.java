package com.redteam.analyze.hunting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ATT&CK 矩阵数据初始化器
 *
 * <p>应用启动时通过 {@link ApplicationRunner} 加载内置 ATT&CK 矩阵数据集，
 * 注入 {@link AttackMatrixService} 的内存存储。</p>
 *
 * <p>低优先级（{@link Order} = 100），确保在业务 Bean 初始化后执行。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(100)
public class AttackMatrixInitializer implements ApplicationRunner {

    private final AttackMatrixService attackMatrixService;

    /**
     * 应用启动时加载 ATT&CK 矩阵
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            attackMatrixService.loadDefaultMatrix();
            log.info("ATT&CK 矩阵初始化完成: 战术={}, 技术={}",
                    attackMatrixService.tacticCount(),
                    attackMatrixService.techniqueCount());
        } catch (Exception e) {
            log.error("ATT&CK 矩阵初始化失败", e);
            // 降级：不阻塞主流程
        }
    }
}
