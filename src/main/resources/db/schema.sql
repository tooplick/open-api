-- ============================================================
-- AI Open Platform  数据库初始化脚本 (MySQL 8.0+)
-- 字符集 utf8mb4, 引擎 InnoDB
-- 不计费: 仅记录 token 用量, 不做额度限制
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
    `must_change_password` TINYINT NOT NULL DEFAULT 0 COMMENT '1需首次登录强制改账号密码 0否',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删 1已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`)
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
    `group`       VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '分组(决定可路由到哪些渠道)',
    `models`      VARCHAR(500) DEFAULT NULL COMMENT '模型白名单(逗号分隔), 空表示不限制',
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
    `api_key`       TEXT         NOT NULL COMMENT '上游密钥(支持多 key, 换行分隔)',
    `models`        TEXT         DEFAULT NULL COMMENT '支持的模型, 逗号分隔',
    `group`         VARCHAR(255) NOT NULL DEFAULT 'default' COMMENT '分组(逗号分隔, 可属多组)',
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
-- 能力表(渠道 group x models 笛卡尔展开, 路由用; 由渠道增改自动维护)
-- ---------------------------
DROP TABLE IF EXISTS `ability`;
CREATE TABLE `ability`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group`      VARCHAR(64)  NOT NULL COMMENT '分组',
    `model`      VARCHAR(100) NOT NULL COMMENT '模型',
    `channel_id` BIGINT       NOT NULL COMMENT '渠道ID',
    `enabled`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可用: 跟随渠道状态',
    `priority`   INT          NOT NULL DEFAULT 0 COMMENT '优先级(拷贝自渠道)',
    `weight`     INT          NOT NULL DEFAULT 1 COMMENT '权重(拷贝自渠道)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_grp_model_chan` (`group`, `model`, `channel_id`),
    KEY `idx_grp_model_enabled` (`group`, `model`, `enabled`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='渠道能力路由表';

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
    `duration_ms`       BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
    `http_status`       INT          DEFAULT NULL COMMENT '上游 HTTP 状态码',
    `is_stream`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否流式: 1是 0否',
    `upstream_model`    VARCHAR(100) DEFAULT NULL COMMENT '映射后实际请求上游的模型',
    `endpoint`          VARCHAR(255) DEFAULT NULL COMMENT '客户端请求端点路径',
    `ttfb_ms`           BIGINT       DEFAULT NULL COMMENT '首字延迟(毫秒)',
    `upstream_ms`       BIGINT       DEFAULT NULL COMMENT '上游耗时(毫秒)',
    `user_agent`        VARCHAR(255) DEFAULT NULL COMMENT '客户端 User-Agent',
    `request_id`        VARCHAR(64)  DEFAULT NULL COMMENT '请求ID',
    `ip`                VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    `content`           VARCHAR(1000) DEFAULT NULL COMMENT '摘要/错误信息',
    `create_time`       DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_channel_id` (`channel_id`),
    KEY `idx_model_name` (`model_name`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='调用日志表';

-- ---------------------------
-- 系统设置表(键值;站点信息/注册开关/默认分组/登录公告 等)
-- ---------------------------
DROP TABLE IF EXISTS `system_setting`;
CREATE TABLE `system_setting`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT         DEFAULT NULL COMMENT '配置值(统一字符串存储)',
    `update_time`  DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统设置表';

-- ---------------------------
-- 管理员账号由应用启动时自动创建(见 DataInitializer): admin / admin
-- 系统设置默认项由应用启动时补齐(见 SettingInitializer)
-- 模型不再手动维护, 由各渠道的 models 字段聚合得到
-- ---------------------------

-- ---------------------------
-- 老库升级说明(本仓库无迁移脚本;schema.sql 为 DROP+CREATE 全量脚本,老库切勿重跑整脚本以免丢数据)
-- 邮箱验证码注册:为 user.email 增加普通索引以加速查重(全新库已含,老库仅执行下面一行):
--   ALTER TABLE `user` ADD KEY `idx_email` (`email`);
-- SMTP 邮件服务配置走 system_setting 键值表, 由 SettingInitializer 启动补默认项, 无需建表/改表
-- ---------------------------
