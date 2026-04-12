package com.redteam.feishu.controller;

import com.lark.oa.event.EventDispatcher;
import com.redteam.feishu.service.FeishuMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/feishu")
@RequiredArgsConstructor
public class FeishuWebhookController {

    private final FeishuMessageService messageService;

    @PostMapping("/webhook")
    public Object handleWebhook(@RequestBody String body) {
        log.info("Received feishu webhook event: {}", body);
        return messageService.handleEvent(body);
    }

    @PostMapping("/challenge")
    public Object handleChallenge(@RequestBody String body) {
        log.info("Received feishu challenge: {}", body);
        return messageService.handleChallenge(body);
    }
}
