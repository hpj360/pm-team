package com.redteam.common.telemetry;

/**
 * 告警分级 (v5.4)
 *
 * <p>定义可观测性告警的严重等级及对应的通知通道，{@link AlertNotifier} 根据
 * 级别选择不同的触达方式（P0 预留电话、P1 飞书加急、P2 飞书普通）。</p>
 *
 * <table border="1">
 *   <caption>告警分级与触达</caption>
 *   <tr><th>级别</th><th>含义</th><th>触达通道</th></tr>
 *   <tr><td>P0</td><td>致命：核心服务不可用</td><td>电话（预留）+ 飞书加急 + @all</td></tr>
 *   <tr><td>P1</td><td>严重：核心功能受损</td><td>飞书加急 + @all</td></tr>
 *   <tr><td>P2</td><td>一般：需关注但非紧急</td><td>飞书普通</td></tr>
 * </table>
 *
 * @author 红方团队
 */
public enum AlertSeverity {

    /** P0 致命：核心服务不可用，触达电话（预留）+ 飞书加急 */
    P0("P0", "致命", "red", true),

    /** P1 严重：核心功能受损，触达飞书加急 + @all */
    P1("P1", "严重", "orange", true),

    /** P2 一般：需关注但非紧急，触达飞书普通 */
    P2("P2", "一般", "blue", false);

    /** 级别代码 */
    private final String code;

    /** 中文描述 */
    private final String label;

    /** 飞书卡片头部颜色模板 */
    private final String cardColor;

    /** 是否加急（@all） */
    private final boolean urgent;

    AlertSeverity(String code, String label, String cardColor, boolean urgent) {
        this.code = code;
        this.label = label;
        this.cardColor = cardColor;
        this.urgent = urgent;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getCardColor() {
        return cardColor;
    }

    public boolean isUrgent() {
        return urgent;
    }
}
