package com.redteam.parse.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 解析结果分页查询 DTO
 *
 * @author 红方团队
 */
@Data
public class ParseQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件名（模糊查询）
     */
    private String fileName;

    /**
     * 解析状态：PENDING/SUCCESS/FAILED
     */
    private String parseStatus;

    /**
     * 当前页码（默认 1）
     */
    private Integer pageNum = 1;

    /**
     * 每页大小（默认 10）
     */
    private Integer pageSize = 10;
}
