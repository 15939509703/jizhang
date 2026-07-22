-- 记账小程序基础数据初始化脚本
-- 仅初始化平台级系统分类模板，不创建用户业务数据
-- MySQL 8.0+

SET NAMES utf8mb4;
SET time_zone = '+00:00';

INSERT INTO `fin_category` (
  `category_no`,
  `scope_type`,
  `book_id`,
  `category_type`,
  `parent_id`,
  `name`,
  `icon`,
  `color`,
  `sort_no`,
  `system_flag`,
  `hidden_flag`,
  `deleted_flag`,
  `creator`,
  `modifier`
) VALUES
  ('SYS_EXPENSE_FOOD',          'SYSTEM', NULL, 'EXPENSE', NULL, '餐饮',     'utensils',        '#F97316',  10, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_SHOPPING',      'SYSTEM', NULL, 'EXPENSE', NULL, '购物',     'shopping-bag',    '#EC4899',  20, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_TRANSPORT',     'SYSTEM', NULL, 'EXPENSE', NULL, '交通',     'bus',             '#3B82F6',  30, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_HOUSING',       'SYSTEM', NULL, 'EXPENSE', NULL, '住房',     'house',           '#8B5CF6',  40, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_ENTERTAINMENT', 'SYSTEM', NULL, 'EXPENSE', NULL, '娱乐',     'gamepad-2',       '#A855F7',  50, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_MEDICAL',       'SYSTEM', NULL, 'EXPENSE', NULL, '医疗',     'heart-pulse',     '#EF4444',  60, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_EDUCATION',     'SYSTEM', NULL, 'EXPENSE', NULL, '教育',     'graduation-cap',  '#14B8A6',  70, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_COMMUNICATION', 'SYSTEM', NULL, 'EXPENSE', NULL, '通讯',     'smartphone',      '#06B6D4',  80, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_UTILITIES',     'SYSTEM', NULL, 'EXPENSE', NULL, '水电燃气', 'lightbulb',       '#EAB308',  90, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_TRAVEL',        'SYSTEM', NULL, 'EXPENSE', NULL, '旅行',     'plane',           '#0EA5E9', 100, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_PET',           'SYSTEM', NULL, 'EXPENSE', NULL, '宠物',     'paw-print',       '#84CC16', 110, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_PERSONAL',      'SYSTEM', NULL, 'EXPENSE', NULL, '个人护理', 'sparkles',        '#D946EF', 120, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_GIFT',          'SYSTEM', NULL, 'EXPENSE', NULL, '人情礼物', 'gift',            '#F43F5E', 130, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_DIGITAL',       'SYSTEM', NULL, 'EXPENSE', NULL, '数码',     'laptop',          '#6366F1', 140, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_INSURANCE',     'SYSTEM', NULL, 'EXPENSE', NULL, '保险',     'shield-check',    '#22C55E', 150, 1, 0, 0, 'system', 'system'),
  ('SYS_EXPENSE_OTHER',         'SYSTEM', NULL, 'EXPENSE', NULL, '其他支出', 'circle-ellipsis', '#64748B', 999, 1, 0, 0, 'system', 'system'),

  ('SYS_INCOME_SALARY',         'SYSTEM', NULL, 'INCOME',  NULL, '工资',     'wallet-cards',    '#16A34A',  10, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_BONUS',          'SYSTEM', NULL, 'INCOME',  NULL, '奖金',     'award',           '#CA8A04',  20, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_PART_TIME',      'SYSTEM', NULL, 'INCOME',  NULL, '兼职',     'briefcase',       '#0891B2',  30, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_INVESTMENT',     'SYSTEM', NULL, 'INCOME',  NULL, '投资收益', 'chart-no-axes-combined', '#2563EB', 40, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_REIMBURSEMENT',  'SYSTEM', NULL, 'INCOME',  NULL, '报销',     'receipt-text',    '#0D9488',  50, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_GIFT',           'SYSTEM', NULL, 'INCOME',  NULL, '礼金',     'gift',            '#DB2777',  60, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_REFUND',         'SYSTEM', NULL, 'INCOME',  NULL, '退款',     'undo-2',          '#9333EA',  70, 1, 0, 0, 'system', 'system'),
  ('SYS_INCOME_OTHER',          'SYSTEM', NULL, 'INCOME',  NULL, '其他收入', 'circle-ellipsis', '#64748B', 999, 1, 0, 0, 'system', 'system')
ON DUPLICATE KEY UPDATE
  `scope_type` = VALUES(`scope_type`),
  `book_id` = VALUES(`book_id`),
  `category_type` = VALUES(`category_type`),
  `name` = VALUES(`name`),
  `icon` = VALUES(`icon`),
  `color` = VALUES(`color`),
  `sort_no` = VALUES(`sort_no`),
  `system_flag` = VALUES(`system_flag`),
  `hidden_flag` = VALUES(`hidden_flag`),
  `deleted_flag` = VALUES(`deleted_flag`),
  `modifier` = VALUES(`modifier`);


