/*
 Navicat Premium Dump SQL

 Source Server         : Xuan的本地
 Source Server Type    : MySQL
 Source Server Version : 80045 (8.0.45)
 Source Host           : localhost:3306
 Source Schema         : awards

 Target Server Type    : MySQL
 Target Server Version : 80045 (8.0.45)
 File Encoding         : 65001

 Date: 23/03/2026 16:51:07
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Create and select database
CREATE DATABASE IF NOT EXISTS `awards` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `awards`;

-- ----------------------------
-- Table structure for biz_award_record
-- ----------------------------
DROP TABLE IF EXISTS `biz_award_record`;
CREATE TABLE `biz_award_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `competition_id` bigint NOT NULL,
  `competition_category_id` bigint NOT NULL,
  `award_scope_id` bigint NOT NULL COMMENT '奖项范围ID',
  `award_level_id` bigint NOT NULL COMMENT '奖项等级ID',
  `award_date` date NOT NULL,
  `semester` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `team_award_count` int NOT NULL DEFAULT 1,
  `owner_dept_id` bigint NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DRAFT',
  `submitter_user_id` bigint NULL DEFAULT NULL,
  `submit_time` datetime NULL DEFAULT NULL,
  `final_audit_time` datetime NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_record_team`(`team_id` ASC) USING BTREE,
  INDEX `idx_record_competition`(`competition_id` ASC) USING BTREE,
  INDEX `idx_record_status`(`status` ASC) USING BTREE,
  INDEX `idx_record_dept`(`owner_dept_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '获奖记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_award_record
-- ----------------------------
INSERT INTO `biz_award_record` VALUES (1, 2, 1, 1, 1, 1, '2026-03-18', '2025-2026-2', '智能车竞赛项目', 1, 1, 'APPROVED', 3, '2026-03-18 16:29:59', '2026-03-18 16:59:44', NULL, 3, 0, '2026-03-18 16:29:04', '2026-03-18 16:59:43');
INSERT INTO `biz_award_record` VALUES (2, 2, 1, 1, 2, 3, '2026-02-25', '2025-2026-1', '数学建模项目', 1, 1, 'APPROVED', 3, '2026-03-18 17:16:55', '2026-03-18 17:17:07', NULL, 5, 0, '2026-03-18 17:15:38', '2026-03-18 17:17:07');
INSERT INTO `biz_award_record` VALUES (3, 2, 2, 2, 2, 4, '2026-02-25', '2025-2026-2', '蓝桥杯项目', 1, 1, 'SCHOOL_REJECTED', 3, '2026-03-18 20:45:34', NULL, NULL, 3, 0, '2026-03-18 20:45:16', '2026-03-18 20:45:48');
INSERT INTO `biz_award_record` VALUES (4, 2, 3, 2, 2, 6, '2026-03-18', '2025-2026-2', '计算机设计作品', 1, 1, 'APPROVED', 3, '2026-03-22 22:35:44', '2026-03-22 22:36:43', NULL, 3, 0, '2026-03-22 22:35:15', '2026-03-22 22:36:43');

-- ----------------------------
-- Table structure for biz_award_record_audit
-- ----------------------------
DROP TABLE IF EXISTS `biz_award_record_audit`;
CREATE TABLE `biz_award_record_audit`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `node_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SCHOOL',
  `action_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/RESUBMIT',
  `from_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `to_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `comment_text` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `auditor_user_id` bigint NOT NULL,
  `auditor_dept_id` bigint NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_record`(`record_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审核轨迹表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_award_record_audit
-- ----------------------------
INSERT INTO `biz_award_record_audit` VALUES (1, 1, 'SCHOOL', 'SUBMIT', 'DRAFT', 'PENDING_SCHOOL', NULL, 3, NULL, '2026-03-18 16:29:59');
INSERT INTO `biz_award_record_audit` VALUES (2, 1, 'SCHOOL', 'APPROVE', 'PENDING_SCHOOL', 'APPROVED', NULL, 1, NULL, '2026-03-18 16:59:44');
INSERT INTO `biz_award_record_audit` VALUES (3, 2, 'SCHOOL', 'SUBMIT', 'DRAFT', 'PENDING_SCHOOL', NULL, 3, NULL, '2026-03-18 17:15:59');
INSERT INTO `biz_award_record_audit` VALUES (4, 2, 'SCHOOL', 'REJECT', 'PENDING_SCHOOL', 'SCHOOL_REJECTED', '重新修改', 1, NULL, '2026-03-18 17:16:29');
INSERT INTO `biz_award_record_audit` VALUES (5, 2, 'SCHOOL', 'SUBMIT', 'SCHOOL_REJECTED', 'PENDING_SCHOOL', NULL, 3, NULL, '2026-03-18 17:16:55');
INSERT INTO `biz_award_record_audit` VALUES (6, 2, 'SCHOOL', 'APPROVE', 'PENDING_SCHOOL', 'APPROVED', NULL, 1, NULL, '2026-03-18 17:17:07');
INSERT INTO `biz_award_record_audit` VALUES (7, 3, 'SCHOOL', 'SUBMIT', 'DRAFT', 'PENDING_SCHOOL', NULL, 3, NULL, '2026-03-18 20:45:34');
INSERT INTO `biz_award_record_audit` VALUES (8, 3, 'SCHOOL', 'REJECT', 'PENDING_SCHOOL', 'SCHOOL_REJECTED', '123', 1, NULL, '2026-03-18 20:45:49');
INSERT INTO `biz_award_record_audit` VALUES (9, 4, 'SCHOOL', 'SUBMIT', 'DRAFT', 'PENDING_SCHOOL', NULL, 3, NULL, '2026-03-22 22:35:44');
INSERT INTO `biz_award_record_audit` VALUES (10, 4, 'SCHOOL', 'APPROVE', 'PENDING_SCHOOL', 'APPROVED', NULL, 1, NULL, '2026-03-22 22:36:43');

-- ----------------------------
-- Table structure for biz_award_record_file
-- ----------------------------
DROP TABLE IF EXISTS `biz_award_record_file`;
CREATE TABLE `biz_award_record_file`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `record_id` bigint NOT NULL,
  `file_id` bigint NOT NULL COMMENT 'sys_file.id',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_file_record`(`record_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '获奖附件关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_award_record_file
-- ----------------------------
INSERT INTO `biz_award_record_file` VALUES (1, 1, 1, 0, '2026-03-18 16:29:40');
INSERT INTO `biz_award_record_file` VALUES (2, 2, 2, 0, '2026-03-18 17:15:48');
INSERT INTO `biz_award_record_file` VALUES (3, 3, 3, 0, '2026-03-18 20:45:23');
INSERT INTO `biz_award_record_file` VALUES (4, 4, 4, 0, '2026-03-22 22:35:35');

-- ----------------------------
-- Table structure for biz_export_task
-- ----------------------------
DROP TABLE IF EXISTS `biz_export_task`;
CREATE TABLE `biz_export_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `export_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'DETAIL/SUMMARY',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `filter_json` json NULL,
  `task_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `creator_user_id` bigint NOT NULL,
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_export_creator`(`creator_user_id` ASC) USING BTREE,
  INDEX `idx_export_status`(`task_status` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '导出任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_export_task
-- ----------------------------
INSERT INTO `biz_export_task` VALUES (1, 'SUMMARY', NULL, NULL, NULL, 'FAILED', 1, '缺少导出模板文件：.\\templates\\summary_template.xlsx', '2026-03-18 17:00:09', '2026-03-18 17:00:09');
INSERT INTO `biz_export_task` VALUES (2, 'SUMMARY', NULL, NULL, NULL, 'FAILED', 1, '缺少导出模板文件：.\\templates\\summary_template.xlsx', '2026-03-18 17:00:10', '2026-03-18 17:00:10');
INSERT INTO `biz_export_task` VALUES (3, 'SUMMARY', NULL, NULL, NULL, 'FAILED', 1, '缺少导出模板文件：.\\templates\\summary_template.xlsx', '2026-03-18 17:17:15', '2026-03-18 17:17:15');
INSERT INTO `biz_export_task` VALUES (4, 'DETAIL', NULL, NULL, NULL, 'FAILED', 1, '缺少导出模板文件：.\\templates\\detail_template.xlsx', '2026-03-18 17:17:16', '2026-03-18 17:17:16');
INSERT INTO `biz_export_task` VALUES (5, 'SUMMARY', NULL, NULL, NULL, 'FAILED', 1, '缺少导出模板文件：.\\templates\\summary_template.xlsx', '2026-03-22 18:05:49', '2026-03-22 18:05:49');
INSERT INTO `biz_export_task` VALUES (6, 'DETAIL', NULL, NULL, NULL, 'FAILED', 1, '缺少导出模板文件：.\\templates\\detail_template.xlsx', '2026-03-22 18:05:54', '2026-03-22 18:05:54');

-- ----------------------------
-- Table structure for biz_team
-- ----------------------------
DROP TABLE IF EXISTS `biz_team`;
CREATE TABLE `biz_team`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `captain_user_id` bigint NOT NULL,
  `owner_dept_id` bigint NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_team_captain`(`captain_user_id` ASC) USING BTREE,
  INDEX `idx_team_dept`(`owner_dept_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '团队表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_team
-- ----------------------------
INSERT INTO `biz_team` VALUES (1, '????A', 3, 1, 'ACTIVE', 'smoke', 0, '2026-03-17 23:42:36', '2026-03-17 23:42:36');
INSERT INTO `biz_team` VALUES (2, '11', 3, 1, 'ACTIVE', '', 0, '2026-03-18 13:26:32', '2026-03-18 13:26:32');
INSERT INTO `biz_team` VALUES (3, 'T-selftest-1773846340557', 3, 1, 'ACTIVE', 'selftest', 0, '2026-03-18 23:05:41', '2026-03-18 23:05:41');

-- ----------------------------
-- Table structure for biz_team_invitation
-- ----------------------------
DROP TABLE IF EXISTS `biz_team_invitation`;
CREATE TABLE `biz_team_invitation`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `invitee_user_id` bigint NOT NULL,
  `invitee_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MEMBER/TEACHER',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  `inviter_user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_inv_team`(`team_id` ASC) USING BTREE,
  INDEX `idx_inv_invitee`(`invitee_user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '团队邀请表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_team_invitation
-- ----------------------------
INSERT INTO `biz_team_invitation` VALUES (1, 3, 4, 'MEMBER', 'PENDING', 3, '2026-03-18 23:05:41', '2026-03-18 23:05:41');

-- ----------------------------
-- Table structure for biz_team_member
-- ----------------------------
DROP TABLE IF EXISTS `biz_team_member`;
CREATE TABLE `biz_team_member`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `member_order_no` int NOT NULL DEFAULT 1,
  `is_captain` tinyint(1) NOT NULL DEFAULT 0,
  `join_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACCEPTED',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_team_user`(`team_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_tm_team`(`team_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '团队成员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_team_member
-- ----------------------------
INSERT INTO `biz_team_member` VALUES (1, 1, 3, 1, 1, 'ACCEPTED');
INSERT INTO `biz_team_member` VALUES (2, 2, 3, 1, 1, 'ACCEPTED');
INSERT INTO `biz_team_member` VALUES (3, 3, 3, 1, 1, 'ACCEPTED');

-- ----------------------------
-- Table structure for biz_team_teacher
-- ----------------------------
DROP TABLE IF EXISTS `biz_team_teacher`;
CREATE TABLE `biz_team_teacher`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `teacher_user_id` bigint NOT NULL,
  `teacher_order_no` int NOT NULL DEFAULT 1,
  `join_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACCEPTED',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tt_team`(`team_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '团队指导教师表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of biz_team_teacher
-- ----------------------------

-- ----------------------------
-- Table structure for dict_award_level
-- ----------------------------
DROP TABLE IF EXISTS `dict_award_level`;
CREATE TABLE `dict_award_level`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL COMMENT '所属竞赛ID',
  `award_scope_id` bigint NOT NULL COMMENT '奖项范围ID',
  `level_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '等级名称：如一等奖、二等奖',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_comp_scope_level`(`competition_id` ASC, `award_scope_id` ASC, `level_name` ASC) USING BTREE,
  INDEX `idx_level_comp`(`competition_id` ASC) USING BTREE,
  INDEX `idx_level_scope`(`award_scope_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '奖项等级字典（按竞赛+范围配置）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_award_level
-- ----------------------------
-- 竞赛1：全国大学生智能汽车竞赛 (competition_id=1)
INSERT INTO `dict_award_level` VALUES (1, 1, 1, '其他奖项', 1, 0);
INSERT INTO `dict_award_level` VALUES (2, 1, 2, '一等奖', 1, 0);
INSERT INTO `dict_award_level` VALUES (3, 1, 2, '二等奖', 1, 1);
INSERT INTO `dict_award_level` VALUES (4, 1, 2, '三等奖', 1, 2);
-- 竞赛2：蓝桥杯全国软件和信息技术专业人才大赛 (competition_id=2)
INSERT INTO `dict_award_level` VALUES (5, 2, 2, '一等奖', 1, 0);
INSERT INTO `dict_award_level` VALUES (6, 2, 2, '二等奖', 1, 1);
INSERT INTO `dict_award_level` VALUES (7, 2, 2, '三等奖', 1, 2);
INSERT INTO `dict_award_level` VALUES (8, 2, 7, '一等奖', 1, 0);
INSERT INTO `dict_award_level` VALUES (9, 2, 7, '二等奖', 1, 1);
INSERT INTO `dict_award_level` VALUES (10, 2, 7, '三等奖', 1, 2);
-- 竞赛3：中国大学生计算机设计大赛 (competition_id=3)
INSERT INTO `dict_award_level` VALUES (11, 3, 2, '一等奖', 1, 0);
INSERT INTO `dict_award_level` VALUES (12, 3, 2, '二等奖', 1, 1);

-- ----------------------------
-- Table structure for dict_award_scope
-- ----------------------------
DROP TABLE IF EXISTS `dict_award_scope`;
CREATE TABLE `dict_award_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scope_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '范围名称：如国家级、省级、校级',
  `scope_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '范围编码：NATIONAL/PROVINCIAL/SCHOOL',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scope_code`(`scope_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 8 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '奖项范围字典（公共池）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_award_scope
-- ----------------------------
INSERT INTO `dict_award_scope` VALUES (1, '其他', 'OTHER', 1, 0);
INSERT INTO `dict_award_scope` VALUES (2, '国家级', 'NATIONAL', 1, 1);
INSERT INTO `dict_award_scope` VALUES (3, '省级', 'PROVINCIAL', 1, 2);
INSERT INTO `dict_award_scope` VALUES (4, '校级', 'SCHOOL', 1, 3);

-- ----------------------------
-- Table structure for dict_class
-- ----------------------------
DROP TABLE IF EXISTS `dict_class`;
CREATE TABLE `dict_class`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dept_id` bigint NOT NULL,
  `class_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_dept_class`(`dept_id` ASC, `class_name` ASC) USING BTREE,
  INDEX `idx_class_dept`(`dept_id` ASC) USING BTREE,
  INDEX `idx_class_enabled`(`enabled` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '班级字典（按院系归属）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_class
-- ----------------------------

-- ----------------------------
-- Table structure for dict_competition
-- ----------------------------
DROP TABLE IF EXISTS `dict_competition`;
CREATE TABLE `dict_competition`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '竞赛全称',
  `competition_short_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '竞赛简称',
  `category_id` bigint NOT NULL COMMENT '竞赛类别ID',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注说明',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_comp_category`(`category_id` ASC) USING BTREE,
  INDEX `idx_comp_enabled`(`enabled` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '竞赛字典' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_competition
-- ----------------------------
INSERT INTO `dict_competition` VALUES (1, '全国大学生智能汽车竞赛', '智能车竞赛', 1, 1, 0, '教育部认可的A类竞赛');
INSERT INTO `dict_competition` VALUES (2, '蓝桥杯全国软件和信息技术专业人才大赛', '蓝桥杯', 2, 1, 1, '教育部认可的A类竞赛');
INSERT INTO `dict_competition` VALUES (3, '中国大学生计算机设计大赛', '计算机设计大赛', 2, 1, 2, '教育部认可的A类竞赛');
INSERT INTO `dict_competition` VALUES (4, '全国大学生数学建模竞赛', '数学建模', 1, 1, 3, '教育部认可的A类竞赛');

-- ----------------------------
-- Table structure for dict_competition_category
-- ----------------------------
DROP TABLE IF EXISTS `dict_competition_category`;
CREATE TABLE `dict_competition_category`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '类别名称',
  `category_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类别编码',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_no` int NOT NULL DEFAULT 0,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_category_code`(`category_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '竞赛类别字典' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_competition_category
-- ----------------------------
INSERT INTO `dict_competition_category` VALUES (1, '工程实践类', 'ENGINEERING', 1, 0, '工程实践相关竞赛');
INSERT INTO `dict_competition_category` VALUES (2, '学科竞赛类', 'ACADEMIC', 1, 1, '学科相关竞赛');
INSERT INTO `dict_competition_category` VALUES (3, '创新创业类', 'INNOVATION', 1, 2, '创新创业相关竞赛');

-- ----------------------------
-- Table structure for dict_organizer
-- ----------------------------
DROP TABLE IF EXISTS `dict_organizer`;
CREATE TABLE `dict_organizer`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organizer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主办单位名称',
  `organizer_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '单位类型：MINISTRY/ASSOCIATION/ENTERPRISE/UNIVERSITY',
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_no` int NOT NULL DEFAULT 0,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '主办单位字典（公共池）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_organizer
-- ----------------------------
INSERT INTO `dict_organizer` VALUES (1, '教育部', 'MINISTRY', 1, 0, '中华人民共和国教育部');
INSERT INTO `dict_organizer` VALUES (2, '工业和信息化部', 'MINISTRY', 1, 1, '中华人民共和国工业和信息化部');
INSERT INTO `dict_organizer` VALUES (3, '中国高等教育学会', 'ASSOCIATION', 1, 2, NULL);
INSERT INTO `dict_organizer` VALUES (4, '中国计算机学会', 'ASSOCIATION', 1, 3, NULL);
INSERT INTO `dict_organizer` VALUES (5, '中国自动化学会', 'ASSOCIATION', 1, 4, NULL);
INSERT INTO `dict_organizer` VALUES (6, '中国工业与应用数学学会', 'ASSOCIATION', 1, 5, NULL);
INSERT INTO `dict_organizer` VALUES (7, '教育部高等学校计算机类专业教学指导委员会', 'ASSOCIATION', 1, 6, NULL);

-- ----------------------------
-- Table structure for dict_competition_organizer
-- ----------------------------
DROP TABLE IF EXISTS `dict_competition_organizer`;
CREATE TABLE `dict_competition_organizer`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `competition_id` bigint NOT NULL COMMENT '竞赛ID',
  `organizer_id` bigint NOT NULL COMMENT '主办方ID',
  `is_primary` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否主要主办方：0-否 1-是',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_comp_organizer`(`competition_id` ASC, `organizer_id` ASC) USING BTREE,
  INDEX `idx_co_competition`(`competition_id` ASC) USING BTREE,
  INDEX `idx_co_organizer`(`organizer_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '竞赛-主办方关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of dict_competition_organizer
-- ----------------------------
-- 竞赛1：全国大学生智能汽车竞赛
INSERT INTO `dict_competition_organizer` VALUES (1, 1, 1, 1, 0);
INSERT INTO `dict_competition_organizer` VALUES (2, 1, 5, 0, 1);
-- 竞赛2：蓝桥杯
INSERT INTO `dict_competition_organizer` VALUES (3, 2, 2, 1, 0);
INSERT INTO `dict_competition_organizer` VALUES (4, 2, 3, 0, 1);
INSERT INTO `dict_competition_organizer` VALUES (5, 2, 4, 0, 2);
-- 竞赛3：中国大学生计算机设计大赛
INSERT INTO `dict_competition_organizer` VALUES (6, 3, 1, 1, 0);
INSERT INTO `dict_competition_organizer` VALUES (7, 3, 7, 0, 1);
-- 竞赛4：全国大学生数学建模竞赛
INSERT INTO `dict_competition_organizer` VALUES (8, 4, 1, 1, 0);
INSERT INTO `dict_competition_organizer` VALUES (9, 4, 6, 0, 1);

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `config_value` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `value_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'STRING' COMMENT 'STRING/INT/BOOL/JSON',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `updated_by` bigint NULL DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `config_key`(`config_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统参数配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, 'max_teacher_count', '3', 'INT', '团队指导教师数量上限', NULL, '2026-03-18 23:55:32');
INSERT INTO `sys_config` VALUES (2, 'record_attachment_required', '0', 'BOOL', '提交/保存是否强制要求至少一个附件', NULL, '2026-03-18 23:55:32');
INSERT INTO `sys_config` VALUES (3, 'upload_max_single_file_mb', '50', 'INT', '上传单文件大小限制（MB）', NULL, '2026-03-18 23:55:32');
INSERT INTO `sys_config` VALUES (4, 'allow_admin_supplement_after_deadline', '1', 'BOOL', '截止后是否允许管理员补录（预留）', NULL, '2026-03-18 23:55:32');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dept_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `dept_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `parent_id` bigint NULL DEFAULT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `sort_no` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `dept_code`(`dept_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '院系组织表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (1, 'CS', '计算机学院', NULL, 1, 1);

-- ----------------------------
-- Table structure for sys_file
-- ----------------------------
DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `file_ext` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `storage_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `preview_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'image/pdf/download_only',
  `file_size` bigint NOT NULL DEFAULT 0,
  `uploader_user_id` bigint NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_file_deleted`(`deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件元数据（M2临时表，后续可合并到业务附件表）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_file
-- ----------------------------
INSERT INTO `sys_file` VALUES (1, 'GxPGftEaIAQlgP7.png', 'png', 'image/png', '.\\data\\files\\2026-03-18\\4b9d11c6-12e5-4c4b-b417-78d197ea1771.png', 'image', 64450, 3, '2026-03-18 16:29:40', 0);
INSERT INTO `sys_file` VALUES (2, '96499bfa31f0a5e734ffcf4681e5b566.png', 'png', 'image/png', '.\\data\\files\\2026-03-18\\7d3ca91b-82a8-4fa9-8438-25027a249220.png', 'image', 877433, 3, '2026-03-18 17:15:48', 0);
INSERT INTO `sys_file` VALUES (3, '5e991d3dc9069a1fcdd5525d44ffddc1.png', 'png', 'image/png', '.\\data\\files\\2026-03-18\\69bc770d-e629-4ad8-ad66-97dea7de1b46.png', 'image', 356204, 3, '2026-03-18 20:45:23', 0);
INSERT INTO `sys_file` VALUES (4, '关于开展2025年度团员教育评议及团内五四评优工作的通知.pdf', 'pdf', 'application/pdf', '.\\data\\files\\2026-03-22\\69f13168-a012-434e-81bc-13625f250b05.pdf', 'pdf', 126213, 3, '2026-03-22 22:35:35', 0);

-- ----------------------------
-- Table structure for sys_message
-- ----------------------------
DROP TABLE IF EXISTS `sys_message`;
CREATE TABLE `sys_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiver_user_id` bigint NOT NULL COMMENT '接收者 sys_user.id',
  `msg_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'INVITATION/AUDIT/NOTICE',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `biz_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'TEAM_INVITATION/AWARD_RECORD等',
  `biz_id` bigint NULL DEFAULT NULL COMMENT '业务主键，如 invitationId/recordId',
  `read_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_msg_receiver_created`(`receiver_user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_msg_receiver_read`(`receiver_user_id` ASC, `read_flag` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站内消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_message
-- ----------------------------
INSERT INTO `sys_message` VALUES (1, 3, 'AUDIT', '审核通过', '你提交的获奖填报（ID=4，项目：123）已审核通过。', 'AWARD_RECORD', 4, 0, '2026-03-22 22:36:43');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'CAPTAIN', '队长');
INSERT INTO `sys_role` VALUES (2, 'TEACHER', '指导教师');
INSERT INTO `sys_role` VALUES (3, 'DEPT_ADMIN', '院系管理员');
INSERT INTO `sys_role` VALUES (4, 'SCHOOL_ADMIN', '校级管理员');
INSERT INTO `sys_role` VALUES (5, 'SYS_ADMIN', '系统管理员');

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `real_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'STUDENT/TEACHER/ADMIN',
  `dept_id` bigint NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `student_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '学生学号（可空；非空唯一）',
  `teacher_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '教师工号（可空；非空唯一）',
  `class_id` bigint NULL DEFAULT NULL COMMENT '班级ID（仅学生使用；可空）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_sys_user_student_no`(`student_no` ASC) USING BTREE,
  UNIQUE INDEX `uk_sys_user_teacher_no`(`teacher_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '本地用户表（后续可被外部同步覆盖基础字段）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'school_admin', '$2a$10$qlH/yvWp5ABXW4csypgDruvB9UtoZV/SohDz81oFAQAA2rD7F3MHm', '校级管理员', 'ADMIN', 1, 1, '2026-03-17 22:22:11', '2026-03-17 22:22:11', NULL, NULL, NULL);
INSERT INTO `sys_user` VALUES (2, 'dept_admin', '$2a$10$ljAJQjIbPBbuqlMrxVRdRu0HQdqrdD3k1UNVxjGiVWVL7fZoSPqre', '院系管理员', 'ADMIN', 1, 1, '2026-03-17 22:22:11', '2026-03-17 22:22:11', NULL, NULL, NULL);
INSERT INTO `sys_user` VALUES (3, 'student1', '$2a$10$IWLb4oGXR2j/UqUk59Y/.ewAUs1YHKb/n20.SHjdxdDEH7aamqwHC', '学生1', 'STUDENT', 1, 1, '2026-03-17 22:22:11', '2026-03-17 22:22:11', NULL, NULL, NULL);
INSERT INTO `sys_user` VALUES (4, 'teacher1', '$2a$10$jAzSscr9cL.QrEnCWqeVG.oujtpUdPP0kjjAHeuHhL8uVp/CJKcjG', '教师1', 'TEACHER', 1, 1, '2026-03-17 22:22:11', '2026-03-17 22:22:11', NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_ur_user`(`user_id` ASC) USING BTREE,
  INDEX `idx_ur_role`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户-角色关联' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1, 4);
INSERT INTO `sys_user_role` VALUES (2, 2, 3);
INSERT INTO `sys_user_role` VALUES (3, 3, 1);
INSERT INTO `sys_user_role` VALUES (4, 4, 2);

-- ----------------------------
-- Table structure for sys_operate_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_operate_log`;
CREATE TABLE `sys_operate_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NULL DEFAULT NULL,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `http_method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `request_uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `biz_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `request_body` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `response_code` int NULL DEFAULT NULL,
  `success_flag` tinyint(1) NOT NULL DEFAULT 1,
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `cost_ms` bigint NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_op_user_time`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_op_uri_time`(`request_uri` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_op_biz`(`biz_type` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `success_flag` tinyint(1) NOT NULL DEFAULT 1,
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_login_username_time`(`username` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_login_user_time`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_login_success_time`(`success_flag` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE utf8mb4_general_ci COMMENT = '登录日志' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
