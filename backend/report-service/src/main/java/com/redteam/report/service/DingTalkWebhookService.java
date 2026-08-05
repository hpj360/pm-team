package com.redteam.report.service;

import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 钉钉 Webhook 推送服务
 *
 * <p>通过钉钉自定义机器人 Webhook 发送定时报告生成通知，
 * 使用 markdown 类型消息展示结构化内容。
 * Webhook URL 通过配置项 {@code report.dingtalk.webhook-url} 注入，
 * 为空时跳过推送并记录日志。</p>
 *
 * <p><b>容错策略：</b>推送失败仅记录错误日志，不抛出异常，
 * 避免影响定时报告生成主流程。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class DingTalkWebhookService {

    /**
     * 钉钉 Webhook URL（为空时跳过推送）
     */
    @Value("${report.dingtalk.webhook-url:}")
    private String dingtalkWebhookUrl;

    /**
     * HTTP 客户端，构造时默认创建，测试可通过包级构造器注入
     */
    private final RestTemplate restTemplate;

    /**
     * 默认构造器：Spring 实例化时使用，创建默认 RestTemplate。
     */
    public DingTalkWebhookService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 测试用构造器：可注入自定义 RestTemplate（如绑定 MockRestServiceServer）。
     *
     * @param restTemplate 自定义 RestTemplate
     */
    DingTalkWebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 发送钉钉 Webhook 推送。
     *
     * <p>payload 格式：</p>
     * <pre>{@code
     * {
     *   "msgtype": "markdown",
     *   "markdown": {
     *     "title": "报告通知",
     *     "text": "### 报告已生成\n\n- **报告名称**: {reportName}\n- **摘要**: {summary}\n- **下载链接**: [点击下载]({url})"
     *   }
     * }
     * }</pre>
     *
     * @param reportName 报告名称
     * @param reportUrl  报告下载链接
     * @param summary    报告摘要
     * @return true 表示推送成功；false 表示跳过或失败
     */
    public boolean sendNotification(String reportName, String reportUrl, String summary) {
        if (dingtalkWebhookUrl == null || dingtalkWebhookUrl.isBlank()) {
            log.warn("钉钉 Webhook URL 未配置，跳过推送: reportName={}", reportName);
            return false;
        }
        try {
            String text = "### 报告已生成\n\n"
                    + "- **报告名称**: " + nullToEmpty(reportName) + "\n"
                    + "- **摘要**: " + nullToEmpty(summary) + "\n"
                    + "- **下载链接**: [点击下载](" + nullToEmpty(reportUrl) + ")";

            JSONObject markdown = new JSONObject();
            markdown.set("title", "报告通知");
            markdown.set("text", text);

            JSONObject payload = new JSONObject();
            payload.set("msgtype", "markdown");
            payload.set("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            restTemplate.postForEntity(dingtalkWebhookUrl, request, String.class);
            log.info("钉钉 Webhook 推送成功: reportName={}", reportName);
            return true;
        } catch (Exception e) {
            log.error("钉钉 Webhook 推送失败: reportName={}, url={}", reportName, dingtalkWebhookUrl, e);
            return false;
        }
    }

    /**
     * null 安全转字符串。
     *
     * @param value 原始值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
