package com.redteam.analyze.controller;

import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.dto.MispEvent;
import com.redteam.analyze.service.MispSyncService;
import com.redteam.common.annotation.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MISP 手动同步控制器
 *
 * <p>提供 MISP 集成的手动操作端点与状态查询：</p>
 * <ul>
 *   <li>{@code POST /api/intel/misp/sync/{iocId}}：手动同步单个 IOC</li>
 *   <li>{@code POST /api/intel/misp/sync-all}：手动批量同步</li>
 *   <li>{@code POST /api/intel/misp/pull}：手动拉取 MISP 事件</li>
 *   <li>{@code GET /api/intel/misp/status}：MISP 连接状态 + 最近同步时间</li>
 * </ul>
 *
 * <p>当 MISP 未启用（{@code misp.enabled=false}）时，同步端点静默返回，
 * status 端点返回 enabled=false 状态。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/intel/misp")
@RequiredArgsConstructor
@Tag(name = "MISP 集成", description = "MISP 手动同步与状态查询")
public class MispController {

    private final MispSyncService mispSyncService;

    private final MispProperties mispProperties;

    /**
     * 手动同步单个 IOC 至 MISP
     *
     * @param iocId IOC ID
     * @return 同步结果（含 MISP 事件 ID）
     */
    @PostMapping("/sync/{iocId}")
    @Operation(summary = "手动同步单个 IOC", description = "将指定 IOC 推送至 MISP 创建事件")
    @AuditLog(action = "SYNC", resourceType = "INTEL", resourceIdParam = "iocId", description = "手动同步 IOC 至 MISP")
    public ResponseEntity<Map<String, Object>> syncIoc(
            @Parameter(description = "IOC ID") @PathVariable Long iocId) {
        log.info("手动同步 IOC 至 MISP: iocId={}", iocId);
        MispEvent event = mispSyncService.syncIocToMisp(iocId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("iocId", iocId);
        if (event != null && event.getId() != null) {
            body.put("success", true);
            body.put("mispEventId", event.getId());
            body.put("info", event.getInfo());
        } else if (!mispProperties.isEnabled()) {
            body.put("success", false);
            body.put("message", "MISP 未启用");
        } else {
            body.put("success", false);
            body.put("message", "同步失败，请查看日志");
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 手动批量同步全部 IOC 至 MISP
     *
     * @return 同步结果统计（total / success / fail）
     */
    @PostMapping("/sync-all")
    @Operation(summary = "手动批量同步", description = "将平台全部 IOC 推送至 MISP")
    @AuditLog(action = "SYNC", resourceType = "INTEL", description = "手动批量同步 IOC 至 MISP")
    public ResponseEntity<Map<String, Object>> syncAll() {
        log.info("手动批量同步 IOC 至 MISP");
        Map<String, Object> stat = mispSyncService.syncAllIocsToMisp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.putAll(stat);
        if (!mispProperties.isEnabled()) {
            body.put("message", "MISP 未启用，已静默跳过");
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 手动拉取 MISP 事件至平台 IOC 库
     *
     * @return 拉取结果统计（events / attributes / saved）
     */
    @PostMapping("/pull")
    @Operation(summary = "手动拉取 MISP 事件", description = "从 MISP 拉取事件写入平台 IOC 库")
    @AuditLog(action = "SYNC", resourceType = "INTEL", description = "手动拉取 MISP 事件")
    public ResponseEntity<Map<String, Object>> pull() {
        log.info("手动拉取 MISP 事件");
        Map<String, Object> stat = mispSyncService.pullMispEvents();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.putAll(stat);
        if (!mispProperties.isEnabled()) {
            body.put("message", "MISP 未启用，已静默跳过");
        }
        return ResponseEntity.ok(body);
    }

    /**
     * 查询 MISP 连接状态与最近同步时间
     *
     * @return 状态信息（enabled / endpoint / lastSyncTime / lastPullTime / lastError）
     */
    @GetMapping("/status")
    @Operation(summary = "MISP 状态", description = "查询 MISP 连接状态与最近同步时间")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", mispProperties.isEnabled());
        body.put("endpoint", mispProperties.getEndpoint());
        body.put("lastSyncTime", mispSyncService.getLastSyncTime());
        body.put("lastPullTime", mispSyncService.getLastPullTime());
        body.put("lastError", mispSyncService.getLastError());
        body.put("serverTime", LocalDateTime.now());
        return ResponseEntity.ok(body);
    }
}
