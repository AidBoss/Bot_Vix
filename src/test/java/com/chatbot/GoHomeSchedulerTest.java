package com.chatbot;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GoHomeSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void testMonthWith5Saturdays_August2026() {
        // August 2026 Saturdays: 1, 8, 15, 22, 29
        LocalDate sat1 = LocalDate.of(2026, 8, 1);
        LocalDate sat2 = LocalDate.of(2026, 8, 8);
        LocalDate sat3 = LocalDate.of(2026, 8, 15);
        LocalDate sat4 = LocalDate.of(2026, 8, 22);
        LocalDate sat5 = LocalDate.of(2026, 8, 29);

        // First & last should be true
        assertTrue(GoHomeScheduler.isWorkSaturday(sat1), "Thứ 7 đầu tháng (1/8) phải là true");
        assertTrue(GoHomeScheduler.isWorkSaturday(sat5), "Thứ 7 cuối tháng (29/8) phải là true");

        // Middle saturdays should be false
        assertFalse(GoHomeScheduler.isWorkSaturday(sat2), "Thứ 7 tuần 2 (8/8) phải là false");
        assertFalse(GoHomeScheduler.isWorkSaturday(sat3), "Thứ 7 tuần 3 (15/8) phải là false");
        assertFalse(GoHomeScheduler.isWorkSaturday(sat4), "Thứ 7 tuần 4 (22/8) phải là false");
    }

    @Test
    void testMonthWith4Saturdays_February2026() {
        // February 2026 Saturdays: 7, 14, 21, 28
        LocalDate sat1 = LocalDate.of(2026, 2, 7);
        LocalDate sat2 = LocalDate.of(2026, 2, 14);
        LocalDate sat3 = LocalDate.of(2026, 2, 21);
        LocalDate sat4 = LocalDate.of(2026, 2, 28);

        assertTrue(GoHomeScheduler.isWorkSaturday(sat1), "Thứ 7 đầu tháng (7/2) phải là true");
        assertTrue(GoHomeScheduler.isWorkSaturday(sat4), "Thứ 7 cuối tháng (28/2) phải là true");

        assertFalse(GoHomeScheduler.isWorkSaturday(sat2), "Thứ 7 tuần 2 (14/2) phải là false");
        assertFalse(GoHomeScheduler.isWorkSaturday(sat3), "Thứ 7 tuần 3 (21/2) phải là false");
    }

    @Test
    void testShouldNotifyOnDate() {
        // Sunday should never notify
        LocalDate sunday = LocalDate.of(2026, 8, 2);
        assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());
        assertFalse(GoHomeScheduler.shouldNotifyOnDate(sunday), "Chủ nhật không được gửi thông báo");

        // Monday to Friday should always notify
        for (int day = 3; day <= 7; day++) {
            LocalDate weekday = LocalDate.of(2026, 8, day);
            assertTrue(GoHomeScheduler.shouldNotifyOnDate(weekday), "Ngày thường T2-T6 phải gửi thông báo: " + weekday);
        }

        // Saturday 1st of month: notify
        LocalDate sat1 = LocalDate.of(2026, 8, 1);
        assertTrue(GoHomeScheduler.shouldNotifyOnDate(sat1));

        // Saturday middle: do not notify
        LocalDate sat2 = LocalDate.of(2026, 8, 8);
        assertFalse(GoHomeScheduler.shouldNotifyOnDate(sat2));
    }

    @Test
    void testNotificationTimes() {
        LocalDate weekday = LocalDate.of(2026, 8, 3); // Monday
        LocalDate saturday = LocalDate.of(2026, 8, 1); // Saturday

        LocalTime weekdayTime = GoHomeScheduler.getNotificationTimeForDate(weekday, 17, 35, 17, 0);
        assertEquals(LocalTime.of(17, 35), weekdayTime, "Ngày thường phải là 17:35");

        LocalTime satTime = GoHomeScheduler.getNotificationTimeForDate(saturday, 17, 35, 17, 0);
        assertEquals(LocalTime.of(17, 0), satTime, "Thứ 7 phải là 17:00 (5h chiều)");
    }

    @Test
    void testCalculateNextRun_FromFridayEveningToEligibleSaturday() {
        // Friday, Aug 28, 2026 18:00 (after weekday go-home)
        // Next eligible day is Saturday, Aug 29 (last Saturday of month) at 17:00
        ZonedDateTime from = ZonedDateTime.of(2026, 8, 28, 18, 0, 0, 0, ZONE);
        ZonedDateTime next = GoHomeScheduler.calculateNextRun(from, 17, 35, 17, 0);

        assertEquals(ZonedDateTime.of(2026, 8, 29, 17, 0, 0, 0, ZONE), next);
    }

    @Test
    void testCalculateNextRun_FromFridayEveningToMiddleSaturday_SkipsToMonday() {
        // Friday, Aug 7, 2026 18:00 (after weekday go-home)
        // Saturday, Aug 8 is 2nd Saturday (middle) -> skip!
        // Sunday, Aug 9 is Sunday -> skip!
        // Next eligible day is Monday, Aug 10 at 17:35
        ZonedDateTime from = ZonedDateTime.of(2026, 8, 7, 18, 0, 0, 0, ZONE);
        ZonedDateTime next = GoHomeScheduler.calculateNextRun(from, 17, 35, 17, 0);

        assertEquals(ZonedDateTime.of(2026, 8, 10, 17, 35, 0, 0, ZONE), next);
    }

    @Test
    void testCalculateNextRun_FromSundayToMonday() {
        // Sunday, Aug 9, 2026 10:00
        // Next eligible day is Monday, Aug 10 at 17:35
        ZonedDateTime from = ZonedDateTime.of(2026, 8, 9, 10, 0, 0, 0, ZONE);
        ZonedDateTime next = GoHomeScheduler.calculateNextRun(from, 17, 35, 17, 0);

        assertEquals(ZonedDateTime.of(2026, 8, 10, 17, 35, 0, 0, ZONE), next);
    }
}
