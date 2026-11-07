ALTER TABLE `fin_book_member`
  ADD COLUMN `menu_permissions` JSON DEFAULT NULL COMMENT '成员菜单权限；新成员须显式配置',
  ADD COLUMN `transaction_scope` VARCHAR(16) DEFAULT NULL COMMENT '账单范围：ALL或OWN_CREATED',
  MODIFY COLUMN `policy_version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成员策略乐观锁版本';

ALTER TABLE `fin_book_invitation`
  ADD COLUMN `menu_permissions` JSON DEFAULT NULL COMMENT '邀请时的菜单权限快照',
  ADD COLUMN `action_permissions` JSON DEFAULT NULL COMMENT '邀请时的操作权限快照',
  ADD COLUMN `transaction_scope` VARCHAR(16) DEFAULT NULL COMMENT '邀请时的账单范围快照';

ALTER TABLE `sys_notification_outbox`
  ADD COLUMN `book_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '通知所属账本；旧任务缺失时拒绝发送',
  ADD INDEX `idx_notification_book_status` (`book_id`, `status`);

ALTER TABLE `fin_transaction`
  ADD INDEX `idx_transaction_book_creator_time` (`book_id`, `created_user_id`, `happened_at`, `id`);
