-- Allow reimbursements to be recorded without an existing expense transaction.

ALTER TABLE `fin_reimbursement`
  DROP FOREIGN KEY `fk_reimbursement_expense`;

ALTER TABLE `fin_reimbursement`
  MODIFY COLUMN `expense_transaction_id` BIGINT UNSIGNED DEFAULT NULL;

ALTER TABLE `fin_reimbursement`
  ADD CONSTRAINT `fk_reimbursement_expense`
    FOREIGN KEY (`expense_transaction_id`) REFERENCES `fin_transaction` (`id`) ON DELETE RESTRICT;
