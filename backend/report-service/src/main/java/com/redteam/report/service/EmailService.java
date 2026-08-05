package com.redteam.report.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务
 *
 * <p>封装 {@link JavaMailSender} 提供两类邮件发送能力：</p>
 * <ul>
 *   <li>{@link #sendReport} —— HTML 正文 + 附件的报告邮件</li>
 *   <li>{@link #sendSimpleMail} —— 纯文本邮件</li>
 * </ul>
 *
 * <p><b>容错策略：</b>邮件发送失败仅记录日志，不向上抛出异常，
 * 避免影响报告生成主流程。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    /**
     * Spring 提供的邮件发送器
     */
    private final JavaMailSender mailSender;

    /**
     * 发件人地址（默认 noreply@redteam.com）
     */
    @Value("${spring.mail.username:noreply@redteam.com}")
    private String fromAddress;

    /**
     * 发送带附件的 HTML 报告邮件。
     *
     * <p>使用 {@link MimeMessageHelper} 构造 multipart 消息，支持 HTML 正文与二进制附件。
     * 任意收件人或附件异常均被捕获并记录日志，不抛出异常。</p>
     *
     * @param to             收件人（多个以英文逗号分隔）
     * @param subject        邮件主题
     * @param htmlContent    HTML 正文
     * @param attachment     附件字节数组（可为 null）
     * @param attachmentName 附件文件名（可为 null）
     */
    public void sendReport(String to, String subject, String htmlContent, byte[] attachment, String attachmentName) {
        if (to == null || to.isBlank()) {
            log.warn("邮件发送失败：收件人为空");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to.split(","));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            if (attachment != null && attachment.length > 0 && attachmentName != null && !attachmentName.isBlank()) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
            }
            mailSender.send(message);
            log.info("报告邮件发送成功: to={}, subject={}, attachmentSize={}",
                    to, subject, attachment == null ? 0 : attachment.length);
        } catch (MessagingException e) {
            log.error("报告邮件发送失败（MessagingException）: to={}, subject={}", to, subject, e);
        } catch (RuntimeException e) {
            log.error("报告邮件发送失败（RuntimeException）: to={}, subject={}", to, subject, e);
        }
    }

    /**
     * 发送简单纯文本邮件。
     *
     * @param to      收件人（多个以英文逗号分隔）
     * @param subject 邮件主题
     * @param text    纯文本正文
     */
    public void sendSimpleMail(String to, String subject, String text) {
        if (to == null || to.isBlank()) {
            log.warn("简单邮件发送失败：收件人为空");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to.split(","));
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("简单邮件发送成功: to={}, subject={}", to, subject);
        } catch (RuntimeException e) {
            log.error("简单邮件发送失败: to={}, subject={}", to, subject, e);
        }
    }
}
