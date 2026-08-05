package com.redteam.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.redteam.common.api.dto.SearchTemplateDTO;
import com.redteam.common.api.dto.SearchTemplateVO;
import com.redteam.common.entity.SearchTemplateEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.SearchTemplateMapper;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.UserContext;
import com.redteam.search.service.SearchTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索模板服务实现
 *
 * <p>userId 从 {@link UserContext} 获取，若上下文无法获取则降级使用默认值 1L。
 * params_json 原样存储，不做解析。删除时校验模板所有权（userId 匹配）。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchTemplateServiceImpl implements SearchTemplateService {

    /**
     * 默认用户ID（上下文无法获取时降级使用）
     */
    private static final Long DEFAULT_USER_ID = 1L;

    private final SearchTemplateMapper searchTemplateMapper;

    /**
     * 保存搜索模板
     *
     * @param dto 模板数据
     * @return 保存后的模板视图
     */
    @Override
    public SearchTemplateVO saveTemplate(SearchTemplateDTO dto) {
        // 参数校验
        validateTemplateDTO(dto);

        Long userId = getCurrentUserId();
        log.info("保存搜索模板: userId={}, name={}", userId, dto.getName());

        SearchTemplateEntity entity = new SearchTemplateEntity();
        entity.setUserId(userId);
        entity.setName(dto.getName());
        entity.setParamsJson(dto.getParamsJson());

        searchTemplateMapper.insert(entity);
        log.info("搜索模板保存成功: id={}, userId={}", entity.getId(), userId);

        return toVO(entity);
    }

    /**
     * 查询当前用户的搜索模板列表
     *
     * @return 模板列表
     */
    @Override
    public List<SearchTemplateVO> listTemplates() {
        Long userId = getCurrentUserId();
        log.info("查询搜索模板列表: userId={}", userId);

        List<SearchTemplateEntity> entities = searchTemplateMapper.selectByUserId(userId);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 删除搜索模板（校验所有权）
     *
     * @param id 模板ID
     */
    @Override
    public void deleteTemplate(Long id) {
        Long userId = getCurrentUserId();
        log.info("删除搜索模板: id={}, userId={}", id, userId);

        SearchTemplateEntity entity = searchTemplateMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "搜索模板不存在");
        }

        // 校验所有权
        if (!userId.equals(entity.getUserId())) {
            log.warn("删除搜索模板权限不足: id={}, 当前用户={}, 模板归属用户={}",
                    id, userId, entity.getUserId());
            throw BusinessException.of(ResultCode.FORBIDDEN, "无权删除他人的搜索模板");
        }

        searchTemplateMapper.deleteById(id);
        log.info("搜索模板删除成功: id={}", id);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验模板 DTO 参数
     *
     * @param dto 模板数据
     */
    private void validateTemplateDTO(SearchTemplateDTO dto) {
        if (dto == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "搜索模板参数不能为空");
        }
        if (StrUtil.isBlank(dto.getName())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "模板名称不能为空");
        }
        if (StrUtil.isBlank(dto.getParamsJson())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "搜索条件不能为空");
        }
    }

    /**
     * 获取当前用户ID（降级到默认值 1L）
     *
     * @return 用户ID
     */
    private Long getCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            log.warn("UserContext 未获取到 userId，降级使用默认值: {}", DEFAULT_USER_ID);
            return DEFAULT_USER_ID;
        }
        return userId;
    }

    /**
     * 实体转 VO
     *
     * @param entity 实体
     * @return 视图对象
     */
    private SearchTemplateVO toVO(SearchTemplateEntity entity) {
        SearchTemplateVO vo = new SearchTemplateVO();
        BeanUtil.copyProperties(entity, vo);
        return vo;
    }
}
