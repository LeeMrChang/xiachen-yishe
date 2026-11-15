-- 库：xiachen_yishe（需预先创建，Flyway 不负责建库）
-- 约定：金额 int 存分；不使用外键约束，关系由应用层保证，索引兜底
-- 表设计见 docs/superpowers/specs 设计文档 §5

CREATE TABLE store (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)     NOT NULL,
    code        VARCHAR(32)     NOT NULL,
    address     VARCHAR(255)    NOT NULL DEFAULT '',
    phone       VARCHAR(32)     NOT NULL DEFAULT '',
    open_time   TIME            NULL,
    close_time  TIME            NULL,
    lat         DECIMAL(10, 7)  NULL,
    lng         DECIMAL(10, 7)  NULL,
    cover_url   VARCHAR(512)    NOT NULL DEFAULT '',
    status      VARCHAR(16)     NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF',
    sort_no     INT             NOT NULL DEFAULT 0,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '门店';

CREATE TABLE staff (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id      BIGINT UNSIGNED NULL COMMENT 'ADMIN 可为空，表示跨店',
    username      VARCHAR(64)     NOT NULL,
    password_hash VARCHAR(100)    NOT NULL,
    display_name  VARCHAR(64)     NOT NULL DEFAULT '',
    role          VARCHAR(16)     NOT NULL DEFAULT 'MANAGER' COMMENT 'ADMIN/MANAGER',
    openid        VARCHAR(64)     NULL,
    status        VARCHAR(16)     NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF',
    last_login_at DATETIME(3)     NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_username (username),
    KEY idx_staff_store (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台账号';

CREATE TABLE audit_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    staff_id    BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(64)     NOT NULL,
    target_type VARCHAR(32)     NOT NULL DEFAULT '',
    target_id   BIGINT UNSIGNED NULL,
    detail      JSON            NULL,
    ip          VARCHAR(45)     NOT NULL DEFAULT '',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_staff_time (staff_id, created_at),
    KEY idx_audit_target (target_type, target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台操作留痕';

CREATE TABLE product (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(128)    NOT NULL,
    subtitle     VARCHAR(255)    NOT NULL DEFAULT '',
    category     VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '上装/下装/连衣裙/外套/配饰',
    season       VARCHAR(16)     NOT NULL DEFAULT '' COMMENT 'SS/AW/ALL',
    material     VARCHAR(64)     NOT NULL DEFAULT '',
    description  TEXT            NULL,
    cover_url    VARCHAR(512)    NOT NULL DEFAULT '',
    cover_width  INT             NOT NULL DEFAULT 0 COMMENT '瀑布流布局用',
    cover_height INT             NOT NULL DEFAULT 0 COMMENT '瀑布流布局用',
    status       VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON/OFF，下线用 OFF 不改删',
    sort_weight  INT             NOT NULL DEFAULT 0,
    like_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    fav_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    online_at    DATETIME(3)     NULL COMMENT '上新时间，上新页按此倒序',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_status_category (status, category, sort_weight),
    KEY idx_product_online (status, online_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品 SPU，全局共享';

CREATE TABLE product_image (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id  BIGINT UNSIGNED NOT NULL,
    url         VARCHAR(512)    NOT NULL,
    width       INT             NOT NULL DEFAULT 0,
    height      INT             NOT NULL DEFAULT 0,
    sort_no     INT             NOT NULL DEFAULT 0 COMMENT '0 为主图',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_image_product (product_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品图片';

CREATE TABLE sku (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id        BIGINT UNSIGNED NOT NULL,
    color             VARCHAR(32)     NOT NULL DEFAULT '',
    size              VARCHAR(16)     NOT NULL DEFAULT '',
    price_cent        INT UNSIGNED    NOT NULL DEFAULT 0,
    origin_price_cent INT UNSIGNED    NOT NULL DEFAULT 0,
    sort_no           INT             NOT NULL DEFAULT 0,
    status            VARCHAR(16)     NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_product_color_size (product_id, color, size),
    KEY idx_sku_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'SKU，颜色 x 尺码';

CREATE TABLE inventory (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id   BIGINT UNSIGNED NOT NULL,
    sku_id     BIGINT UNSIGNED NOT NULL,
    quantity   INT             NOT NULL DEFAULT 0,
    on_sale    TINYINT         NOT NULL DEFAULT 1,
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_store_sku (store_id, sku_id),
    KEY idx_inventory_sku (sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '门店库存，手工维护';

CREATE TABLE collection (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64)     NOT NULL,
    cover_url  VARCHAR(512)    NOT NULL DEFAULT '',
    story      TEXT            NULL,
    publish_at DATETIME(3)     NULL,
    status     VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON/OFF',
    sort_no    INT             NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_collection_status (status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列，如 AW26 秋冬';

CREATE TABLE collection_product (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    collection_id BIGINT UNSIGNED NOT NULL,
    product_id    BIGINT UNSIGNED NOT NULL,
    sort_no       INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cp_collection_product (collection_id, product_id),
    KEY idx_cp_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系列内商品';

CREATE TABLE look (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(128)    NOT NULL DEFAULT '',
    description  TEXT            NULL,
    cover_url    VARCHAR(512)    NOT NULL DEFAULT '',
    cover_width  INT             NOT NULL DEFAULT 0,
    cover_height INT             NOT NULL DEFAULT 0,
    status       VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON/OFF',
    sort_no      INT             NOT NULL DEFAULT 0,
    like_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    fav_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_look_status (status, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '造型，一组搭配的商品';

CREATE TABLE look_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    look_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    sort_no    INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_look_item (look_id, product_id),
    KEY idx_look_item_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '造型内商品';

CREATE TABLE home_theme (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64)     NOT NULL,
    cover_url  VARCHAR(512)    NOT NULL DEFAULT '',
    copy_text  VARCHAR(255)    NOT NULL DEFAULT '',
    start_at   DATETIME(3)     NULL,
    end_at     DATETIME(3)     NULL,
    status     VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON/OFF',
    is_current TINYINT         NOT NULL DEFAULT 0 COMMENT '同一时刻仅一套为 1，由应用保证',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_theme_window (status, start_at, end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页主题';

CREATE TABLE home_module (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    theme_id    BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(16)     NOT NULL COMMENT 'BANNER/COLLECTION/RECOMMEND/STORE/LOOK',
    ref_id      BIGINT UNSIGNED NULL,
    image_url   VARCHAR(512)    NOT NULL DEFAULT '',
    sort_no     INT             NOT NULL DEFAULT 0,
    enabled     TINYINT         NOT NULL DEFAULT 1,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_module_theme (theme_id, enabled, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '首页模块编排';

CREATE TABLE app_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appid         VARCHAR(32)     NOT NULL,
    openid        VARCHAR(64)     NOT NULL,
    unionid       VARCHAR(64)     NULL,
    nickname      VARCHAR(64)     NOT NULL DEFAULT '微信用户',
    phone         VARCHAR(32)     NULL,
    avatar_url    VARCHAR(512)    NOT NULL DEFAULT '',
    last_login_at DATETIME(3)     NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_appid_openid (appid, openid),
    KEY idx_user_unionid (unionid),
    KEY idx_user_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '微信用户，多 AppID';

CREATE TABLE user_favorite (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type TINYINT         NOT NULL COMMENT '1 商品 2 造型',
    target_id   BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fav_user_target (user_id, target_type, target_id),
    KEY idx_fav_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收藏，多态指向商品或造型';

CREATE TABLE user_like (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type TINYINT         NOT NULL COMMENT '1 商品 2 造型',
    target_id   BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_like_user_target (user_id, target_type, target_id),
    KEY idx_like_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '点赞，与收藏语义不同';

CREATE TABLE user_footprint (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    store_id   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    viewed_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fp_user_product_store (user_id, product_id, store_id),
    KEY idx_fp_user_time (user_id, viewed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '足迹，保留 30 天';

CREATE TABLE tryon_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    sku_id     BIGINT UNSIGNED NOT NULL,
    store_id   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tryon_user_sku_store (user_id, sku_id, store_id),
    KEY idx_tryon_user_store (user_id, store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '试穿清单';

CREATE TABLE appointment (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    store_id          BIGINT UNSIGNED NOT NULL,
    visit_date        DATE            NOT NULL,
    visit_time        TIME            NULL COMMENT '30 分钟一档，可为空',
    item_count        INT             NOT NULL DEFAULT 0,
    phone             VARCHAR(32)     NOT NULL,
    contact_name      VARCHAR(32)     NOT NULL DEFAULT '',
    remark            VARCHAR(255)    NOT NULL DEFAULT '',
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/DONE/CANCELED',
    client_request_id VARCHAR(64)     NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_apt_user_request (user_id, client_request_id),
    KEY idx_apt_store_date (store_id, visit_date, status),
    KEY idx_apt_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预约到店，不扣库存';

CREATE TABLE appointment_item (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT UNSIGNED NOT NULL,
    sku_id         BIGINT UNSIGNED NOT NULL,
    store_id       BIGINT UNSIGNED NOT NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_apt_item_appointment (appointment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预约包含的商品';

CREATE TABLE transfer_order (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL,
    sku_id            BIGINT UNSIGNED NOT NULL,
    from_store_id     BIGINT UNSIGNED NOT NULL,
    to_store_id       BIGINT UNSIGNED NOT NULL,
    phone             VARCHAR(32)     NOT NULL,
    remark            VARCHAR(255)    NOT NULL DEFAULT '',
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_TRANSIT/ARRIVED/NOTIFIED/CANCELED',
    client_request_id VARCHAR(64)     NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_user_request (user_id, client_request_id),
    KEY idx_transfer_to_status (to_store_id, status),
    KEY idx_transfer_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '跨店调货意向，不锁库存';
