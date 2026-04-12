package com.redteam.feishu.service;

public interface FeishuMessageService {

    Object handleEvent(String body);

    Object handleChallenge(String body);

    void sendMessage(String receiveId, String receiveIdType, String message);
}
