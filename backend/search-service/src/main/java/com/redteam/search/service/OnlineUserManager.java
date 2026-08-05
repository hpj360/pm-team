package com.redteam.search.service;

import com.redteam.search.dto.OnlineUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线用户管理器（内存实现）
 *
 * <p>使用 {@link ConcurrentHashMap} 按 fileId 维护正在查看该文件的在线用户集合，
 * 同一文件下按 userId 去重。所有操作均为线程安全的，适用于并发 WebSocket 场景。</p>
 *
 * <p>注意：本实现为单实例内存方案，不支持多实例间共享。若需横向扩展，
 * 可替换为 Redis 等分布式存储。</p>
 *
 * @author 红方团队
 */
@Component
@Slf4j
public class OnlineUserManager {

    /**
     * 文件ID -> 在线用户集合
     * <p>使用 ConcurrentHashMap.newKeySet() 保证集合操作的线程安全</p>
     */
    private final Map<Long, Set<OnlineUser>> fileViewers = new ConcurrentHashMap<>();

    /**
     * 用户加入文件查看
     *
     * <p>若该用户已存在于该文件的在线列表中（按 userId 判断），先移除旧记录再加入，
     * 实现「同用户重连覆盖」语义。</p>
     *
     * @param fileId 文件ID
     * @param user   在线用户信息
     */
    public void userJoined(Long fileId, OnlineUser user) {
        if (fileId == null || user == null || user.getUserId() == null) {
            return;
        }
        Set<OnlineUser> viewers = fileViewers.computeIfAbsent(fileId, k -> ConcurrentHashMap.newKeySet());
        // 先移除同 userId 的旧记录，避免重连后出现重复条目
        viewers.removeIf(existing -> user.getUserId().equals(existing.getUserId()));
        viewers.add(user);
        log.info("用户加入文件查看: fileId={}, userId={}, username={}, 当前在线 {} 人",
                fileId, user.getUserId(), user.getUsername(), viewers.size());
    }

    /**
     * 用户离开文件查看
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    public void userLeft(Long fileId, Long userId) {
        if (fileId == null || userId == null) {
            return;
        }
        Set<OnlineUser> viewers = fileViewers.get(fileId);
        if (viewers == null) {
            return;
        }
        boolean removed = viewers.removeIf(existing -> userId.equals(existing.getUserId()));
        if (removed) {
            log.info("用户离开文件查看: fileId={}, userId={}, 剩余在线 {} 人",
                    fileId, userId, viewers.size());
        }
        // 集合为空时清理，避免内存泄漏
        if (viewers.isEmpty()) {
            fileViewers.remove(fileId, viewers);
        }
    }

    /**
     * 获取指定文件的在线用户列表
     *
     * @param fileId 文件ID
     * @return 在线用户集合（不可变视图，可能为空集合）
     */
    public Set<OnlineUser> getViewers(Long fileId) {
        if (fileId == null) {
            return Collections.emptySet();
        }
        Set<OnlineUser> viewers = fileViewers.get(fileId);
        if (viewers == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(viewers);
    }

    /**
     * 获取指定文件的在线人数
     *
     * @param fileId 文件ID
     * @return 在线人数
     */
    public int getViewerCount(Long fileId) {
        if (fileId == null) {
            return 0;
        }
        Set<OnlineUser> viewers = fileViewers.get(fileId);
        return viewers == null ? 0 : viewers.size();
    }
}
