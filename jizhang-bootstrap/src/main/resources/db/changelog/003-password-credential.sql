CREATE TABLE IF NOT EXISTS `sys_user_credential` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '网页登录账号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `failed_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `locked_until` DATETIME(3) DEFAULT NULL COMMENT '锁定截止时间',
  `last_login_time` DATETIME(3) DEFAULT NULL COMMENT '最近登录时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1正常',
  `creator` VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
  `created_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `modifier` VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '修改人',
  `modified_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_credential_username` (`username`),
  UNIQUE KEY `uk_user_credential_user_id` (`user_id`),
  KEY `idx_user_credential_status` (`status`)
) ENGINE=InnoDB COMMENT='网页登录凭证表';
