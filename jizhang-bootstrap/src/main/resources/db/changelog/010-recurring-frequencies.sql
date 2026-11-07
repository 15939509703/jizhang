ALTER TABLE `fin_recurring_rule`
  ADD COLUMN `execution_month` TINYINT UNSIGNED DEFAULT NULL COMMENT '年度规则执行月份' AFTER `recurrence_type`,
  MODIFY COLUMN `execution_day` TINYINT UNSIGNED DEFAULT NULL COMMENT '月度或年度规则执行日',
  ADD CONSTRAINT `ck_recurring_rule_month` CHECK (`execution_month` IS NULL OR `execution_month` BETWEEN 1 AND 12);
