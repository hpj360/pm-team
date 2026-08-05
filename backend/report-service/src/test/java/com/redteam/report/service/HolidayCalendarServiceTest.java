package com.redteam.report.service;

import com.redteam.report.service.HolidayCalendarService.HolidayInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HolidayCalendarService} 单元测试
 *
 * <p>覆盖中国法定节假日识别、调休补班日识别、报告执行判断逻辑，
 * 以及节假日列表查询。</p>
 *
 * @author 红方团队
 */
class HolidayCalendarServiceTest {

    private HolidayCalendarService holidayCalendarService;

    @BeforeEach
    void setUp() {
        holidayCalendarService = new HolidayCalendarService();
    }

    // ===================== isHoliday =====================

    /**
     * 元旦（2026/1/1）应识别为节假日
     */
    @Test
    @DisplayName("isHoliday - 元旦应识别为节假日")
    void testIsHoliday_NewYear() {
        assertTrue(holidayCalendarService.isHoliday(LocalDate.of(2026, 1, 1)));
    }

    /**
     * 春节假期内任意一天（2026/2/16-2/22）应识别为节假日
     */
    @Test
    @DisplayName("isHoliday - 春节假期应识别为节假日")
    void testIsHoliday_SpringFestival() {
        // 春节首日
        assertTrue(holidayCalendarService.isHoliday(LocalDate.of(2026, 2, 16)));
        // 春节末日
        assertTrue(holidayCalendarService.isHoliday(LocalDate.of(2026, 2, 22)));
        // 春节中间日
        assertTrue(holidayCalendarService.isHoliday(LocalDate.of(2026, 2, 18)));
    }

    /**
     * 普通工作日（2026/3/16，周一）不应识别为节假日
     */
    @Test
    @DisplayName("isHoliday - 普通工作日不应识别为节假日")
    void testIsHoliday_NormalWorkday() {
        // 2026/3/16 是周一
        assertFalse(holidayCalendarService.isHoliday(LocalDate.of(2026, 3, 16)));
    }

    /**
     * 普通周末（2026/3/21，周六）不应识别为节假日（但也不执行报告）
     */
    @Test
    @DisplayName("isHoliday - 普通周末不应识别为节假日")
    void testIsHoliday_Weekend() {
        // 2026/3/21 是周六
        assertFalse(holidayCalendarService.isHoliday(LocalDate.of(2026, 3, 21)));
        // 普通周末也不应执行报告
        assertFalse(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 3, 21)));
    }

    /**
     * 国庆假期首日（2026/10/1）应识别为节假日
     */
    @Test
    @DisplayName("isHoliday - 国庆应识别为节假日")
    void testIsHoliday_NationalDay() {
        assertTrue(holidayCalendarService.isHoliday(LocalDate.of(2026, 10, 1)));
        assertTrue(holidayCalendarService.isHoliday(LocalDate.of(2026, 10, 7)));
    }

    // ===================== isWorkdayAdjustment =====================

    /**
     * 春节调休补班日（2026/2/8，周日）应识别为调休补班日
     */
    @Test
    @DisplayName("isWorkdayAdjustment - 春节调休补班日应识别")
    void testIsWorkdayAdjustment_SpringFestival() {
        // 2026/2/8 是周日，但属于春节调休补班日
        assertTrue(holidayCalendarService.isWorkdayAdjustment(LocalDate.of(2026, 2, 8)));
    }

    /**
     * 普通工作日不应识别为调休补班日
     */
    @Test
    @DisplayName("isWorkdayAdjustment - 普通工作日不应识别")
    void testIsWorkdayAdjustment_NormalWorkday() {
        assertFalse(holidayCalendarService.isWorkdayAdjustment(LocalDate.of(2026, 3, 16)));
    }

    // ===================== shouldExecuteReport =====================

    /**
     * 节假日（2026/1/1 元旦）不应执行报告
     */
    @Test
    @DisplayName("shouldExecuteReport - 节假日不应执行报告")
    void testShouldExecuteReport_Holiday() {
        assertFalse(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 1, 1)));
        assertFalse(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 2, 17)));
        assertFalse(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 10, 3)));
    }

    /**
     * 调休补班日（2026/2/8，周日）应执行报告
     */
    @Test
    @DisplayName("shouldExecuteReport - 调休补班日应执行报告")
    void testShouldExecuteReport_Adjustment() {
        // 2026/2/8 是周日，但属于调休补班日，应执行
        assertTrue(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 2, 8)));
        // 2026/10/10 是周六，但属于国庆调休补班日，应执行
        assertTrue(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 10, 10)));
    }

    /**
     * 普通工作日（2026/3/16，周一）应执行报告
     */
    @Test
    @DisplayName("shouldExecuteReport - 普通工作日应执行报告")
    void testShouldExecuteReport_NormalWorkday() {
        assertTrue(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 3, 16)));
        assertTrue(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 3, 17)));
    }

    /**
     * 普通周末不应执行报告
     */
    @Test
    @DisplayName("shouldExecuteReport - 普通周末不应执行报告")
    void testShouldExecuteReport_Weekend() {
        // 2026/3/21 周六
        assertFalse(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 3, 21)));
        // 2026/3/22 周日
        assertFalse(holidayCalendarService.shouldExecuteReport(LocalDate.of(2026, 3, 22)));
    }

    // ===================== getHolidays =====================

    /**
     * 查询 2026 年节假日列表应包含节假日与调休补班日
     */
    @Test
    @DisplayName("getHolidays - 2026 年应返回节假日与调休补班日列表")
    void testGetHolidays_2026() {
        List<HolidayInfo> holidays = holidayCalendarService.getHolidays(2026);
        assertNotNull(holidays);
        // 7 个节假日段（元旦1 + 春节7 + 清明3 + 劳动节5 + 端午3 + 中秋3 + 国庆7 = 29 天）
        // + 7 个调休补班日 = 36 条
        assertFalse(holidays.isEmpty());

        // 验证包含 HOLIDAY 与 ADJUSTMENT 两种类型
        boolean hasHoliday = holidays.stream()
                .anyMatch(h -> HolidayCalendarService.TYPE_HOLIDAY.equals(h.getType()));
        boolean hasAdjustment = holidays.stream()
                .anyMatch(h -> HolidayCalendarService.TYPE_ADJUSTMENT.equals(h.getType()));
        assertTrue(hasHoliday, "应包含 HOLIDAY 类型");
        assertTrue(hasAdjustment, "应包含 ADJUSTMENT 类型");

        // 验证元旦存在
        boolean hasNewYear = holidays.stream()
                .anyMatch(h -> h.getDate().equals(LocalDate.of(2026, 1, 1))
                        && "元旦".equals(h.getName()));
        assertTrue(hasNewYear, "应包含元旦");
    }

    /**
     * 查询无数据的年份应返回空列表
     */
    @Test
    @DisplayName("getHolidays - 无数据年份应返回空列表")
    void testGetHolidays_EmptyYear() {
        List<HolidayInfo> holidays = holidayCalendarService.getHolidays(2030);
        assertNotNull(holidays);
        assertTrue(holidays.isEmpty());
    }

    /**
     * 节假日列表应按日期升序排列
     */
    @Test
    @DisplayName("getHolidays - 列表应按日期升序排列")
    void testGetHolidays_Sorted() {
        List<HolidayInfo> holidays = holidayCalendarService.getHolidays(2026);
        assertNotNull(holidays);
        for (int i = 1; i < holidays.size(); i++) {
            assertTrue(holidays.get(i - 1).getDate().isBefore(holidays.get(i).getDate())
                    || holidays.get(i - 1).getDate().equals(holidays.get(i).getDate()),
                    "节假日列表应按日期升序排列");
        }
    }

    /**
     * 入参为 null 时的边界判断
     */
    @Test
    @DisplayName("shouldExecuteReport - null 入参默认执行")
    void testShouldExecuteReport_Null() {
        assertTrue(holidayCalendarService.shouldExecuteReport(null));
    }

    /**
     * 2027 年节假日也应可查询
     */
    @Test
    @DisplayName("getHolidays - 2027 年数据应可查询")
    void testGetHolidays_2027() {
        List<HolidayInfo> holidays = holidayCalendarService.getHolidays(2027);
        assertNotNull(holidays);
        assertFalse(holidays.isEmpty());
        // 验证 2027 元旦存在
        boolean has2027NewYear = holidays.stream()
                .anyMatch(h -> h.getDate().equals(LocalDate.of(2027, 1, 1)));
        assertTrue(has2027NewYear, "2027 应包含元旦");
    }
}
