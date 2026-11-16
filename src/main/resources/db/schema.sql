-- ============================================================
-- AI 辅助说明：本文件由 AI 辅助生成/修改
-- 生成内容：MySQL 8 生产建表脚本（由 Hibernate 6 的 schema-generation 按实体导出，非手写猜测）
-- 人工修改：加 IF NOT EXISTS、整理为 DDL 惯用书写顺序
-- 审查状态：待人工审查
-- 用途：prod profile 下 ddl-auto=validate，表结构由本脚本负责创建，
--       Hibernate 只做校验（发现实体与库不一致时启动即报错，而不是悄悄 ALTER 生产库）
-- 幂等：全部 CREATE TABLE IF NOT EXISTS，重复执行安全
-- ============================================================

-- ---------- 1. 用户表 ----------
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，60 字符，列宽 100 留余量',
    email       VARCHAR(100) DEFAULT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 禁用',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 2. 分类表 ----------
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 3. 商品表 ----------
-- 注意：category_id 是裸 BIGINT（实体里没建 @ManyToOne），故意不加外键，
--       这样删除分类的校验收在业务层（AdminCategoryController）而不是靠数据库报错。
CREATE TABLE IF NOT EXISTS product (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100)   NOT NULL,
    description  TEXT,
    price        DECIMAL(10, 2) NOT NULL,
    stock        INT            NOT NULL,
    sales        INT            NOT NULL DEFAULT 0,
    category_id  BIGINT         NOT NULL,
    image_url    VARCHAR(255)   DEFAULT NULL,
    status       ENUM ('ON_SALE','OFF_SHELF') NOT NULL DEFAULT 'ON_SALE',
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_category (category_id),
    KEY idx_product_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 4. 购物车项 ----------
CREATE TABLE IF NOT EXISTS cart_item (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    quantity    INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES t_user (id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 5. 订单主表 ----------
CREATE TABLE IF NOT EXISTS orders (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    order_no          VARCHAR(32)    NOT NULL,
    user_id           BIGINT         NOT NULL,
    total_amount      DECIMAL(10, 2) NOT NULL,
    status            ENUM ('CREATED','PAID','SHIPPED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'CREATED',
    receiver_name     VARCHAR(50)    NOT NULL,
    receiver_phone    VARCHAR(20)    NOT NULL,
    receiver_address  VARCHAR(255)   NOT NULL,
    remark            VARCHAR(255)   DEFAULT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    paid_at           DATETIME(6)    DEFAULT NULL,
    shipped_at        DATETIME(6)    DEFAULT NULL,
    completed_at      DATETIME(6)    DEFAULT NULL,
    cancelled_at      DATETIME(6)    DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user_created (user_id, created_at),
    KEY idx_order_status_created (status, created_at),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES t_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 6. 订单明细 ----------
-- product_id / product_name / product_price 都是下单瞬间的快照，不加外键：
-- 后台删商品后历史订单仍能完整显示（见 P1-7）。
CREATE TABLE IF NOT EXISTS order_item (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    order_id       BIGINT         NOT NULL,
    product_id     BIGINT         NOT NULL,
    product_name   VARCHAR(100)   NOT NULL,
    product_price  DECIMAL(10, 2) NOT NULL,
    quantity       INT            NOT NULL,
    subtotal       DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 7. 邮件日志 ----------
CREATE TABLE IF NOT EXISTS mail_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    order_id       BIGINT       NOT NULL,
    order_no       VARCHAR(32)  DEFAULT NULL,
    type           ENUM ('ORDER_CREATED','ORDER_PAID','ORDER_SHIPPED') NOT NULL,
    status         ENUM ('SENT','FAILED','SKIPPED') NOT NULL,
    to_email       VARCHAR(100) DEFAULT NULL,
    error_message  VARCHAR(200) DEFAULT NULL,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_mail_order (order_id),
    KEY idx_mail_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- ---------- 8. 访问日志 ----------
CREATE TABLE IF NOT EXISTS access_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      DEFAULT NULL,
    target_type  VARCHAR(16) DEFAULT NULL,
    target_id    BIGINT      DEFAULT NULL,
    action       ENUM ('VIEW_LIST','VIEW_PRODUCT','ADD_CART','CREATE_ORDER','PAY_ORDER') NOT NULL,
    ip           VARCHAR(64) DEFAULT NULL,
    session_id   VARCHAR(64) DEFAULT NULL,
    user_agent   VARCHAR(255) DEFAULT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_access_created (created_at),
    KEY idx_access_action (action),
    KEY idx_access_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
