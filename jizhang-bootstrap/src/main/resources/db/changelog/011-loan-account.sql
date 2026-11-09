ALTER TABLE `fin_loan_record`
  ADD COLUMN `account_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '当前借出账户ID' AFTER `related_transaction_id`,
  ADD KEY `idx_loan_account` (`account_id`),
  ADD CONSTRAINT `fk_loan_account` FOREIGN KEY (`account_id`) REFERENCES `fin_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

UPDATE `fin_loan_record` AS loan
JOIN `fin_account_entry` AS entry ON entry.`transaction_id` = loan.`related_transaction_id`
  AND entry.`entry_type` = 'DECREASE'
SET loan.`account_id` = entry.`account_id`
WHERE loan.`loan_type` = 'LEND' AND loan.`account_id` IS NULL;
