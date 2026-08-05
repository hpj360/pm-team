package com.redteam.parse.service;

import com.redteam.parse.dto.NerEntityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自动标签识别异步任务包装
 *
 * <p>将标签识别异步化，保证识别失败不影响文件解析主流程。
 * 调用方在 NER 识别完成后触发本任务，由 {@link TagRecognitionEngine} 执行实际识别。</p>
 *
 * @author 红方团队
 */
@Component
@Slf4j
public class TagRecognitionTask {

    @Autowired
    private TagRecognitionEngine engine;

    /**
     * 异步执行标签识别
     *
     * @param fileId      文件ID
     * @param textContent 文件文本内容
     * @param fileName    文件名
     * @param fileType    文件类型
     * @param nerEntities NER 识别实体列表
     */
    @Async
    public void executeTagRecognition(Long fileId, String textContent, String fileName,
                                      String fileType, List<NerEntityVO> nerEntities) {
        try {
            engine.recognizeTags(fileId, textContent, fileName, fileType, nerEntities);
        } catch (Exception e) {
            log.error("自动标签识别失败: fileId={}", fileId, e);
        }
    }
}
