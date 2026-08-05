package com.redteam.report.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节假日日历服务
 *
 * <p>提供中国法定节假日 + 调休补班日的判断能力，定时报告调度器在触发前调用
 * {@link #shouldExecuteReport(LocalDate)} 决定是否跳过执行。</p>
 *
 * <p><b>数据来源：</b>2026-2027 年中国法定节假日与调休数据硬编码于静态 Map 中，
 * 后续可改为外部配置文件或远程日历 API。当前覆盖：</p>
 * <ul>
 *   <li>元旦、春节、清明、劳动节、端午、中秋、国庆</li>
 *   <li>对应的调休补班日（周末但需上班）</li>
 * </ul>
 *
 * <p><b>判断逻辑：</b></p>
 * <ul>
 *   <li>调休补班日 → 正常执行报告（即使落在周末）</li>
 *   <li>节假日 → 跳过报告执行</li>
 *   <li>普通周末 → 跳过报告执行</li>
 *   <li>普通工作日 → 正常执行报告</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class HolidayCalendarService {

    /**
     * 节假日类型标记
     */
    public static final String TYPE_HOLIDAY = "HOLIDAY";

    /**
     * 调休补班日类型标记
     */
    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";

    /**
     * 节假日数据：year -> (date -> 节假日名称)
     */
    private static final Map<Integer, Map<LocalDate, String>> HOLIDAYS = new HashMap<>();

    /**
     * 调休补班日数据：year -> (date -> 调休说明)
     */
    private static final Map<Integer, Map<LocalDate, String>> ADJUSTMENTS = new HashMap<>();

    static {
        // ========== 2026 年中国法定节假日 + 调休（依据国务院公告）==========
        Map<LocalDate, String> holidays2026 = new HashMap<>();
        // 元旦 1/1
        holidays2026.put(LocalDate.of(2026, 1, 1), "元旦");
        // 春节 2/16-2/22
        for (int d = 16; d <= 22; d++) {
            holidays2026.put(LocalDate.of(2026, 2, d), "春节");
        }
        // 清明 4/4-4/6
        for (int d = 4; d <= 6; d++) {
            holidays2026.put(LocalDate.of(2026, 4, d), "清明");
        }
        // 劳动节 5/1-5/5
        for (int d = 1; d <= 5; d++) {
            holidays2026.put(LocalDate.of(2026, 5, d), "劳动节");
        }
        // 端午 6/19-6/21
        for (int d = 19; d <= 21; d++) {
            holidays2026.put(LocalDate.of(2026, 6, d), "端午");
        }
        // 中秋 9/25-9/27
        for (int d = 25; d <= 27; d++) {
            holidays2026.put(LocalDate.of(2026, 9, d), "中秋");
        }
        // 国庆 10/1-10/7
        for (int d = 1; d <= 7; d++) {
            holidays2026.put(LocalDate.of(2026, 10, d), "国庆");
        }
        HOLIDAYS.put(2026, holidays2026);

        // 2026 调休补班日（周末但需上班）
        Map<LocalDate, String> adj2026 = new HashMap<>();
        adj2026.put(LocalDate.of(2026, 2, 8), "春节调休");
        adj2026.put(LocalDate.of(2026, 2, 15), "春节调休");
        adj2026.put(LocalDate.of(2026, 4, 26), "清明调休");
        adj2026.put(LocalDate.of(2026, 5, 9), "劳动节调休");
        adj2026.put(LocalDate.of(2026, 6, 28), "端午调休");
        adj2026.put(LocalDate.of(2026, 9, 28), "中秋调休");
        adj2026.put(LocalDate.of(2026, 10, 10), "国庆调休");
        ADJUSTMENTS.put(2026, adj2026);

        // ========== 2027 年中国法定节假日（预填参考，待国务院公告后更新）==========
        Map<LocalDate, String> holidays2027 = new HashMap<>();
        // 元旦 1/1
        holidays2027.put(LocalDate.of(2027, 1, 1), "元旦");
        // 2027 春节（农历正月初一为 2027/2/6）
        for (int d = 5; d <= 11; d++) {
            holidays2027.put(LocalDate.of(2027, 2, d), "春节");
        }
        // 清明 4/4-4/6
        for (int d = 4; d <= 6; d++) {
            holidays2027.put(LocalDate.of(2027, 4, d), "清明");
        }
        // 劳动节 5/1-5/5
        for (int d = 1; d <= 5; d++) {
            holidays2027.put(LocalDate.of(2027, 5, d), "劳动节");
        }
        // 2027 端午（农历五月初五为 2027/6/9）
        for (int d = 8; d <= 10; d++) {
            holidays2027.put(LocalDate.of(2027, 6, d), "端午");
        }
        // 2027 中秋（农历八月十五为 2027/9/15）
        for (int d = 15; d <= 17; d++) {
            holidays2027.put(LocalDate.of(2027, 9, d), "中秋");
        }
        // 国庆 10/1-10/7
        for (int d = 1; d <= 7; d++) {
            holidays2027.put(LocalDate.of(2027, 10, d), "国庆");
        }
        HOLIDAYS.put(2027, holidays2027);

        // 2027 调休补班日（预填参考，待官方公告后更新）
        Map<LocalDate, String> adj2027 = new HashMap<>();
        adj2027.put(LocalDate.of(2027, 2, 7), "春节调休");
        adj2027.put(LocalDate.of(2027, 2, 14), "春节调休");
        adj2027.put(LocalDate.of(2027, 4, 25), "清明调休");
        adj2027.put(LocalDate.of(2027, 5, 8), "劳动节调休");
        adj2027.put(LocalDate.of(2027, 6, 12), "端午调休");
        adj2027.put(LocalDate.of(2027, 9, 19), "中秋调休");
        adj2027.put(LocalDate.of(2027, 10, 10), "国庆调休");
        ADJUSTMENTS.put(2027, adj2027);
    }

    /**
     * 判断指定日期是否为节假日（不执行报告）。
     *
     * @param date 日期
     * @return true 表示是法定节假日
     */
    public boolean isHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        Map<LocalDate, String> yearHolidays = HOLIDAYS.get(date.getYear());
        return yearHolidays != null && yearHolidays.containsKey(date);
    }

    /**
     * 判断指定日期是否为调休补班日（正常执行）。
     *
     * @param date 日期
     * @return true 表示是调休补班日
     */
    public boolean isWorkdayAdjustment(LocalDate date) {
        if (date == null) {
            return false;
        }
        Map<LocalDate, String> yearAdj = ADJUSTMENTS.get(date.getYear());
        return yearAdj != null && yearAdj.containsKey(date);
    }

    /**
     * 判断指定日期是否应该执行报告。
     *
     * <p>判断优先级：</p>
     * <ol>
     *   <li>调休补班日 → true（周末但需上班，正常执行）</li>
     *   <li>节假日 → false（跳过）</li>
     *   <li>普通周末 → false（跳过）</li>
     *   <li>其他 → true（普通工作日，正常执行）</li>
     * </ol>
     *
     * @param date 日期
     * @return true 表示需要执行报告
     */
    public boolean shouldExecuteReport(LocalDate date) {
        if (date == null) {
            log.warn("节假日判断入参为 null，默认执行报告");
            return true;
        }
        if (isWorkdayAdjustment(date)) {
            log.debug("调休补班日，正常执行报告: date={}", date);
            return true;
        }
        if (isHoliday(date)) {
            log.debug("节假日，跳过报告执行: date={}", date);
            return false;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.debug("普通周末，跳过报告执行: date={}", date);
            return false;
        }
        return true;
    }

    /**
     * 获取指定年份的节假日与调休补班日列表。
     *
     * <p>返回结果按日期升序排列，包含 HOLIDAY 与 ADJUSTMENT 两种类型。</p>
     *
     * @param year 年份
     * @return 节假日信息列表（无数据时返回空列表）
     */
    public List<HolidayInfo> getHolidays(int year) {
        List<HolidayInfo> result = new ArrayList<>();
        Map<LocalDate, String> yearHolidays = HOLIDAYS.get(year);
        if (yearHolidays != null) {
            yearHolidays.forEach((date, name) -> {
                HolidayInfo info = new HolidayInfo();
                info.setDate(date);
                info.setName(name);
                info.setType(TYPE_HOLIDAY);
                result.add(info);
            });
        }
        Map<LocalDate, String> yearAdj = ADJUSTMENTS.get(year);
        if (yearAdj != null) {
            yearAdj.forEach((date, name) -> {
                HolidayInfo info = new HolidayInfo();
                info.setDate(date);
                info.setName(name);
                info.setType(TYPE_ADJUSTMENT);
                result.add(info);
            });
        }
        result.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return Collections.unmodifiableList(result);
    }

    /**
     * 节假日信息内部类
     */
    @Data
    public static class HolidayInfo {
        /**
         * 日期
         */
        private LocalDate date;

        /**
         * 节假日名称
         */
        private String name;

        /**
         * 类型：HOLIDAY（节假日）/ ADJUSTMENT（调休补班日）
         */
        private String type;
    }
}
