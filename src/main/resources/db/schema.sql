-- ============================================================
-- AI Open Platform  数据库初始化脚本 (MySQL 8.0+)
-- 字符集 utf8mb4, 引擎 InnoDB
-- 额度(quota)单位为抽象"点数",由模型价格 * token 数计算消耗
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ai_open_platform`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `ai_open_platform`;

-- ---------------------------
-- 用户表
-- ---------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT '角色: admin / user',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `quota`       BIGINT       NOT NULL DEFAULT 0 COMMENT '总额度(点数)',
    `used_quota`  BIGINT       NOT NULL DEFAULT 0 COMMENT '已用额度(点数)',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ---------------------------
-- API Key 表(用户调用平台所用的密钥)
-- ---------------------------
DROP TABLE IF EXISTS `api_key`;
CREATE TABLE `api_key`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
    `name`        VARCHAR(100) DEFAULT NULL COMMENT '名称/备注',
    `api_key`     VARCHAR(64)  NOT NULL COMMENT '密钥(sk- 开头)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `quota`       BIGINT       NOT NULL DEFAULT 0 COMMENT '独立额度上限, 0表示不单独限额(跟随用户)',
    `used_quota`  BIGINT       NOT NULL DEFAULT 0 COMMENT '该 key 已用额度',
    `expire_time` DATETIME     DEFAULT NULL COMMENT '过期时间, NULL 表示永不过期',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key` (`api_key`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='API Key 表';

-- ---------------------------
-- 渠道表(上游 AI 服务商接入配置)
-- ---------------------------
DROP TABLE IF EXISTS `channel`;
CREATE TABLE `channel`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100) NOT NULL COMMENT '渠道名称',
    `type`          VARCHAR(32)  NOT NULL DEFAULT 'openai' COMMENT '渠道类型: openai/azure/anthropic 等',
    `base_url`      VARCHAR(255) NOT NULL COMMENT '上游地址, 如 https://api.openai.com',
    `api_key`       VARCHAR(512) NOT NULL COMMENT '上游密钥',
    `models`        TEXT         DEFAULT NULL COMMENT '支持的模型, 逗号分隔',
    `model_mapping` TEXT         DEFAULT NULL COMMENT '模型重命名映射(JSON), 可选',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `weight`        INT          NOT NULL DEFAULT 1 COMMENT '权重(同优先级内按权重随机)',
    `priority`      INT          NOT NULL DEFAULT 0 COMMENT '优先级, 越大越优先',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='渠道表';

-- ---------------------------
-- 模型表(模型元信息与计费)
-- ---------------------------
DROP TABLE IF EXISTS `model`;
CREATE TABLE `model`
(
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `model_name`       VARCHAR(100)  NOT NULL COMMENT '模型标识, 如 gpt-4o',
    `display_name`     VARCHAR(100)  DEFAULT NULL COMMENT '展示名',
    `type`             VARCHAR(32)   NOT NULL DEFAULT 'chat' COMMENT '类型: chat/embedding/image',
    `prompt_price`     DECIMAL(12, 6) NOT NULL DEFAULT 0 COMMENT '输入每 token 消耗点数',
    `completion_price` DECIMAL(12, 6) NOT NULL DEFAULT 0 COMMENT '输出每 token 消耗点数',
    `status`           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `remark`           VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `create_time`      DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time`      DATETIME      DEFAULT NULL COMMENT '更新时间',
    `deleted`          TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_name` (`model_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='模型表';

-- ---------------------------
-- 调用日志表(不做逻辑删除,无 deleted 字段)
-- ---------------------------
DROP TABLE IF EXISTS `log`;
CREATE TABLE `log`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`           BIGINT       DEFAULT NULL COMMENT '用户ID',
    `username`          VARCHAR(50)  DEFAULT NULL COMMENT '用户名(冗余)',
    `api_key_id`        BIGINT       DEFAULT NULL COMMENT 'API Key ID',
    `channel_id`        BIGINT       DEFAULT NULL COMMENT '渠道ID',
    `channel_name`      VARCHAR(100) DEFAULT NULL COMMENT '渠道名(冗余)',
    `model_name`        VARCHAR(100) DEFAULT NULL COMMENT '模型',
    `type`              TINYINT      NOT NULL DEFAULT 1 COMMENT '类型: 1成功 2失败',
    `prompt_tokens`     INT          NOT NULL DEFAULT 0 COMMENT '输入 token',
    `completion_tokens` INT          NOT NULL DEFAULT 0 COMMENT '输出 token',
    `total_tokens`      INT          NOT NULL DEFAULT 0 COMMENT '总 token',
    `quota`             BIGINT       NOT NULL DEFAULT 0 COMMENT '本次消耗点数',
    `duration_ms`       BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
    `request_id`        VARCHAR(64)  DEFAULT NULL COMMENT '请求ID',
    `ip`                VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    `content`           VARCHAR(500) DEFAULT NULL COMMENT '摘要/错误信息',
    `create_time`       DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_channel_id` (`channel_id`),
    KEY `idx_model_name` (`model_name`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='调用日志表';

-- ---------------------------
-- 预置常见模型(价格为 0, 即默认不计费; 可按需修改)
-- 管理员账号由应用启动时自动创建(见 DataInitializer): admin / admin
-- ---------------------------
INSERT INTO `model` (`model_name`, `display_name`, `type`, `prompt_price`, `completion_price`, `status`, `create_time`, `update_time`)
VALUES ('gpt-4o', 'GPT-4o', 'chat', 0, 0, 1, NOW(), NOW()),
       ('gpt-4o-mini', 'GPT-4o mini', 'chat', 0, 0, 1, NOW(), NOW()),
       ('gpt-3.5-turbo', 'GPT-3.5 Turbo', 'chat', 0, 0, 1, NOW(), NOW());
