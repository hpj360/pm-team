package com.redteam.parse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件解析结果实体类
 *
 * <p>对应数据库表 t_parse_result，持久化 Tika 提取的文本、SM3 哈希、
 * 解析状态以及 YARA/NER 增强解析结果关联。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_parse_result")
public class ParseResultEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID（唯一）
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 提取的文本内容
     */
    private String textContent;

    /**
     * 文本内容 SM3 哈希
     */
    private String textHash;

    /**
     * 检测到的语言
     */
    private String language;

    /**
     * 文件编码
     */
    private String encoding;

    /**
     * 页数（PDF/Word）
     */
    private Integer pageCount;

    /**
     * 解析状态：PENDING/SUCCESS/FAILED
     */
    private String parseStatus;

    /**
     * 解析错误信息
     */
    private String parseError;

    /**
     * 解析耗时（毫秒）
     */
    private Long parseDurationMs;
}
