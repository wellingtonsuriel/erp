package com.pos_onlineshop.hybrid.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on Spring's @Scheduled annotation support - see ScheduledJobsService for what
 * actually runs on a schedule. Deliberately does NOT schedule period-close or the financial
 * adjustments it wires in (accrual reversal, depreciation, FX revaluation, IAS 29
 * restatement) - closing an accounting period is a deliberate business decision a human
 * triggers via the API (AccountingPeriodService.closePeriod), not something to run blindly
 * on a cron with no one reviewing the result. */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
