package com.redteam.analyze.service;

import com.redteam.analyze.entity.IoCEntity;

import java.util.List;

/**
 * IOC 服务接口
 *
 * <p>提供 IOC 数据的查询、保存/更新与去重能力，供 MISP 同步、Webhook 接收
 * 等场景调用。</p>
 *
 * @author 红方团队
 */
public interface IoCService {

    /**
     * 根据 ID 查询 IOC
     *
     * @param id IOC ID
     * @return IOC 实体，不存在返回 null
     */
    IoCEntity getById(Long id);

    /**
     * 查询全部 IOC
     *
     * @return IOC 列表
     */
    List<IoCEntity> listAll();

    /**
     * 保存或更新 IOC
     *
     * <p>当同类型同值的 IOC 已存在时执行更新，否则执行新增。
     * 同时回填 {@code createTime} / {@code updateTime} / {@code lastSeen}。</p>
     *
     * @param ioc IOC 实体
     * @return 保存后的 IOC 实体（含 ID）
     */
    IoCEntity saveOrUpdateIoc(IoCEntity ioc);

    /**
     * 根据类型与值查找 IOC（用于去重判断）
     *
     * @param iocType IOC 类型
     * @param iocValue IOC 值
     * @return 匹配的 IOC 实体，不存在返回 null
     */
    IoCEntity findByTypeAndValue(String iocType, String iocValue);
}
