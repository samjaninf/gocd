/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.server.presentation.models;

import com.thoughtworks.go.util.Dates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TimeConverterTest {
    private TimeConverter timeConverter;

    @BeforeEach
    public void setUp() {
        this.timeConverter = new TimeConverter();
    }

    @Test
    public void testShouldReturn() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        assertEquals(new TimeConverter.ConvertedTime(TimeConverter.getHumanReadableDate(Dates.from(now))),
                timeConverter.getConvertedTime(Dates.from(now), Dates.from(yesterday)));
    }

    @Test
    public void testShouldReportLessThanOneMinutesFor0To29Seconds() {
        assertEquals(TimeConverter.LESS_THAN_A_MINUTE_AGO, timeConverter.getConvertedTime(29));
    }

    @Test
    public void testShouldReportOneMinuteFor30Seconds() {
        assertEquals(TimeConverter.ABOUT_1_MINUTE_AGO, timeConverter.getConvertedTime(30));
    }

    @Test
    public void testShouldReportOneMinuteFor89Seconds() {
        assertEquals(TimeConverter.ABOUT_1_MINUTE_AGO, timeConverter.getConvertedTime(89));
    }

    @Test
    public void testShouldReport2To44MinutesFor90Seconds() {
        assertEquals(TimeConverter.ABOUT_X_MINUTES_AGO.argument(2), timeConverter
                .getConvertedTime(1 * 60 + 30));
    }

    @Test
    public void testShouldReport1DayFor45Minutes() {
        assertEquals(TimeConverter.ABOUT_1_HOUR_AGO, timeConverter.getConvertedTime(45 * 60));
    }

    @Test
    public void testShouldReport44MinutesFor44Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_X_MINUTES_AGO.argument(44), timeConverter
                .getConvertedTime(44 * 60 + 29));
    }

    @Test
    public void testShouldReportAbout1HourFor44Minutes30Seconds() {
        assertEquals(TimeConverter.ABOUT_1_HOUR_AGO, timeConverter.getConvertedTime(44 * 60 + 30));
    }

    @Test
    public void testShouldReportAbout1HourFor89Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_1_HOUR_AGO, timeConverter.getConvertedTime(89 * 60 + 29));
    }

    @Test
    public void testShouldReportAbout2HoursHourFor89Minutes30Seconds() {
        assertEquals(TimeConverter.ABOUT_X_HOURS_AGO.argument(2), timeConverter
                .getConvertedTime(1 * TimeConverter.HOUR_IN_SECONDS + 29 * 60 + 30));
    }

    @Test
    public void testShouldReport23HoursFor23Hours59Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_X_HOURS_AGO.argument(23), timeConverter
                .getConvertedTime(24 * TimeConverter.HOUR_IN_SECONDS - 31));
    }

    @Test
    public void testShouldReportAbout1DayFor23Hours59Minutes30Seconds() {
        assertEquals(TimeConverter.ABOUT_1_DAY_AGO, timeConverter.getConvertedTime(23 * 60 * 60 + 59 * 60 + 30));
    }

    @Test
    public void testShouldReportAbout1DayFor47Hours59Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_1_DAY_AGO, timeConverter.getConvertedTime(47 * 60 * 60 + 59 * 60 + 29));
    }

    @Test
    public void testShouldReport2DaysFor47Hours59Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_X_DAYS_AGO.argument(2), timeConverter
                .getConvertedTime(2 * TimeConverter.DAY_IN_SECONDS - 30));
    }

    @Test
    public void testShouldReport29DaysFor29Days23Hours59Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_X_DAYS_AGO.argument(29), timeConverter
                .getConvertedTime(30 * TimeConverter.DAY_IN_SECONDS - 31));
    }

    @Test
    public void testShouldReportAbout1MonthFor29Days23Hours59Minutes30Seconds() {
        assertEquals(TimeConverter.ABOUT_1_MONTH_AGO, timeConverter.getConvertedTime(29
                * TimeConverter.DAY_IN_SECONDS + 23 * 60 * 60 + 59 * 60 + 30));
    }

    @Test
    public void testShouldReportAbout1MonthFor59Days23Hours59Minutes29Seconds() {
        assertEquals(TimeConverter.ABOUT_1_MONTH_AGO, timeConverter.getConvertedTime(59
                * TimeConverter.DAY_IN_SECONDS + 23 * 60 * 60 + 59 * 60 + 29));
    }

    @Test
    public void testShouldReport2MonthsFor59Days23Hours59Minutes30Seconds() {
        assertEquals(TimeConverter.ABOUT_X_MONTHS_AGO.argument(2), timeConverter
                .getConvertedTime(60 * TimeConverter.DAY_IN_SECONDS - 30));
    }

    @Test
    public void testShouldReport12MonthsFor59Days23Hours59Minutes30Seconds() {
        assertEquals(TimeConverter.ABOUT_X_MONTHS_AGO.argument(12), timeConverter
                .getConvertedTime(365 * TimeConverter.DAY_IN_SECONDS - 31));
    }

    @Test
    public void testShouldReportAbout1YearFor1YearMinus30Seconds() {
        assertEquals(TimeConverter.ABOUT_1_YEAR_AGO, timeConverter
                .getConvertedTime(365 * TimeConverter.DAY_IN_SECONDS - 30));
    }

    @Test
    public void testShouldReportAbout1YearFor2YearsMinus31Seconds() {
        assertEquals(TimeConverter.ABOUT_1_YEAR_AGO, timeConverter
                .getConvertedTime(2 * 365 * TimeConverter.DAY_IN_SECONDS - 31));
    }

    @Test
    public void testShouldReturnTimeUnitAsYearsWhenDurationIsLargerThan2Years() {
        assertEquals(TimeConverter.OVER_X_YEARS_AGO.argument(2), timeConverter
                .getConvertedTime(2 * 365 * TimeConverter.DAY_IN_SECONDS - 30));
    }

    @Test
    public void testShouldReturnTimeUnitAsYearsWhenDurationIsLargerThan3Years() {
        assertEquals(TimeConverter.OVER_X_YEARS_AGO.argument(3), timeConverter
                .getConvertedTime(3 * 365 * TimeConverter.DAY_IN_SECONDS + 2 * TimeConverter.DAY_IN_SECONDS));
    }

    @Test
    public void testShouldReturnNotAvailableWhenInputDateIsNull() {
        assertEquals(TimeConverter.ConvertedTime.NOT_AVAILABLE, timeConverter.getConvertedTime((Date) null));
    }
}
