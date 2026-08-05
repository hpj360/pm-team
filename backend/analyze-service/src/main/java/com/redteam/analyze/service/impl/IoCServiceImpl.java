package com.redteam.analyze.service.impl;

import com.redteam.analyze.entity.IoCEntity;
import com.redteam.analyze.entity.IocType;
import com.redteam.analyze.service.IoCService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IOC 服务实现（内存 Mock 数据源）
 *
 * <p>当前版本使用 {@link ConcurrentHashMap} 作为内存数据源，预置若干 Mock IOC 数据，
 * 与 {@code StixExportController} 的 Mock 风格保持一致，便于在数据库表未就绪时
 * 跑通 MISP 集成链路。后续可替换为基于 {@code IoCMapper} 的持久化实现，
 * 仅需保持 {@link IoCService} 接口方法签名不变。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class IoCServiceImpl implements IoCService {

    /**
     * 内存数据源：ID -> IOC 实体
     */
    private final Map<Long, IoCEntity> store = new ConcurrentHashMap<>();

    /**
     * 自增 ID 生成器
     */
    private final AtomicLong idSeq = new AtomicLong(0);

    public IoCServiceImpl() {
        // 预置 Mock IOC 数据
        saveOrUpdateIoc(buildIoc(IocType.IP, "1.2.3.4", "恶意 C2 服务器 IP", "MANUAL", "2"));
        saveOrUpdateIoc(buildIoc(IocType.DOMAIN, "evil.com", "恶意域名", "MANUAL", "2"));
        saveOrUpdateIoc(buildIoc(IocType.URL, "http://evil.com/payload", "恶意载荷下载地址", "SANDBOX", "1"));
        saveOrUpdateIoc(buildIoc(IocType.MD5, "d41d8cd98f00b204e9800998ecf8427e", "恶意样本 MD5", "SANDBOX", "1"));
        saveOrUpdateIoc(buildIoc(IocType.SHA256,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "恶意样本 SHA256", "SANDBOX", "1"));
        saveOrUpdateIoc(buildIoc(IocType.EMAIL, "attacker@evil.com", "攻击者邮箱", "MANUAL", "3"));
        log.info("IoCServiceImpl 初始化完成，预置 {} 条 Mock IOC 数据", store.size());
    }

    /**
     * 构建 IOC 实体（不含 ID 与时间字段，由 saveOrUpdateIoc 回填）
     */
    private IoCEntity buildIoc(String type, String value, String desc, String source, String threatLevel) {
        IoCEntity ioc = new IoCEntity();
        ioc.setIocType(type);
        ioc.setIocValue(value);
        ioc.setDescription(desc);
        ioc.setSource(source);
        ioc.setThreatLevel(threatLevel);
        return ioc;
    }

    @Override
    public IoCEntity getById(Long id) {
        if (id == null) {
            return null;
        }
        return store.get(id);
    }

    @Override
    public List<IoCEntity> listAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public IoCEntity saveOrUpdateIoc(IoCEntity ioc) {
        if (ioc == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        // 去重：同类型同值视为同一 IOC
        IoCEntity existing = findByTypeAndValue(ioc.getIocType(), ioc.getIocValue());
        if (existing != null) {
            // 更新
            ioc.setId(existing.getId());
            ioc.setCreateTime(existing.getCreateTime());
            ioc.setUpdateTime(now);
            if (ioc.getFirstSeen() == null) {
                ioc.setFirstSeen(existing.getFirstSeen());
            }
            ioc.setLastSeen(now);
            // 保留已同步的 MISP 事件 ID（除非显式覆盖）
            if (ioc.getMispEventId() == null) {
                ioc.setMispEventId(existing.getMispEventId());
            }
            store.put(existing.getId(), ioc);
            log.debug("更新 IOC: id={}, type={}, value={}", existing.getId(), ioc.getIocType(), ioc.getIocValue());
            return ioc;
        }
        // 新增
        long newId = idSeq.incrementAndGet();
        ioc.setId(newId);
        if (ioc.getCreateTime() == null) {
            ioc.setCreateTime(now);
        }
        ioc.setUpdateTime(now);
        if (ioc.getFirstSeen() == null) {
            ioc.setFirstSeen(now);
        }
        ioc.setLastSeen(now);
        store.put(newId, ioc);
        log.debug("新增 IOC: id={}, type={}, value={}", newId, ioc.getIocType(), ioc.getIocValue());
        return ioc;
    }

    @Override
    public IoCEntity findByTypeAndValue(String iocType, String iocValue) {
        if (iocType == null || iocValue == null) {
            return null;
        }
        return store.values().stream()
                .filter(i -> Objects.equals(i.getIocType(), iocType)
                        && Objects.equals(i.getIocValue(), iocValue))
                .findFirst()
                .orElse(null);
    }
}
