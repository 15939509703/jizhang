-- Distinguish advance reimbursements from reimbursements that are actual income.

ALTER TABLE `fin_reimbursement`
  ADD COLUMN `reimbursement_type` VARCHAR(16) NOT NULL DEFAULT 'ADVANCE'
    COMMENT '报销类型：ADVANCE-垫付报销，INCOME-收入报销'
    AFTER `reimbursement_transaction_id`,
  ADD CONSTRAINT `ck_reimbursement_type`
    CHECK (`reimbursement_type` IN ('ADVANCE', 'INCOME'));
