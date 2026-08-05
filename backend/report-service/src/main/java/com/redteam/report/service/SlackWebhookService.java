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
 * Slack Webhook 推送服务
 *
 * <p>通过 Slack Incoming Webhook 发送定时报告生成通知。
 * Webhook URL 通过配置项 {@code report.slack.webhook-url} 注入，
 * 为空时跳过推送并记录日志。</p>
 *
 * <p><b>容错策略：</b>推送失败仅记录错误日志，不抛出异常，
 * 避免影响定时报告生成主流程。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class SlackWebhookService {

    /**
     * Slack Webhook URL（为空时跳过推送）
     */
    @Value("${report.slack.webhook-url:}")
    private String slackWebhookUrl;

    /**
     * HTTP 客户端，构造时默认创建，测试可通过包级构造器注入
     */
    private final RestTemplate restTemplate;

    /**
     * 默认构造器：Spring 实例化时使用，创建默认 RestTemplate。
     */
    public SlackWebhookService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 测试用构造器：可注入自定义 RestTemplate（如绑定 MockRestServiceServer）。
     *
     * @param restTemplate 自定义 RestTemplate
     */
    SlackWebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 发送 Slack Webhook 推送。
     *
     * <p>payload 格式：</p>
     * <pre>{@code
     * {"text": "📄 报告已生成: {reportName}\n摘要: {summary}\n下载: {url}"}
     * }</pre>
     *
     * @param reportName 报告名称
     * @param reportUrl  报告下载链接
     * @param summary    报告摘要
     * @return true 表示推送成功；false 表示跳过或失败
     */
    public boolean sendNotification(String reportName, String reportUrl, String summary) {
        if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            log.warn("Slack Webhook URL 未配置，跳过推送: reportName={}", reportName);
            return false;
        }
        try {
            String text = "📄 报告已生成: " + nullToEmpty(reportName)
                    + "\n摘要: " + nullToEmpty(summary)
                    + "\n下载: " + nullToEmpty(reportUrl);
            JSONObject payload = new JSONObject();
            payload.set("text", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            restTemplate.postForEntity(slackWebhookUrl, request, String.class);
            log.info("Slack Webhook 推送成功: reportName={}", reportName);
            return true;
        } catch (Exception e) {
            log.error("Slack Webhook 推送失败: reportName={}, url={}", reportName, slackWebhookUrl, e);
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
