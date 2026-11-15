-- 演示数据：素材取自原型 xia-chenchen-prototype，仅用于本地/测试环境
-- 图片走 xc-api 内置静态目录 /seed/，上线前替换为 COS 地址
-- 10 个商品：首页 4 款 + 上新页 6 款，每款 2 色 x 3 尺码，三店库存有差异

INSERT INTO product (id, title, subtitle, category, season, material, description,
                     cover_url, cover_width, cover_height, status, sort_weight,
                     like_count, fav_count, online_at)
VALUES (1, '燕麦色落肩针织开衫', '落肩版型，慵懒不过分', '上装', 'AW', '羊毛混纺',
        '柔软亲肤的羊毛混纺纱线，落肩袖设计，杏调燕麦色好搭又不挑人。',
        'http://127.0.0.1:8081/seed/p-red.jpg', 1440, 1920, 'ON', 100, 128, 46, '2026-10-28 10:00:00'),
       (2, '茶褐直筒西裤', '垂坠利落，通勤友好', '下装', 'AW', '醋酸混纺',
        '高腰直筒剪裁，醋酸混纺面料自然垂坠，茶褐色比黑色更柔和。',
        'http://127.0.0.1:8081/seed/p-gray.jpg', 1080, 1440, 'ON', 95, 96, 31, '2026-10-30 10:00:00'),
       (3, '焦糖棕羊毛短外套', '秋冬主角单品', '外套', 'AW', '羊毛',
        '双面羊毛呢，短款廓形修饰比例，焦糖棕是这一季的定番色。',
        'http://127.0.0.1:8081/seed/p-hoodie.jpg', 1440, 1834, 'ON', 120, 214, 88, '2026-11-01 10:00:00'),
       (4, '奶油白高领打底衫', '软糯亲肤的基础款', '上装', 'AW', '羊绒混纺',
        '高领不压脖子，奶油白打底提亮整体色调。',
        'http://127.0.0.1:8081/seed/p-sweat.jpg', 1280, 1566, 'ON', 90, 87, 25, '2026-10-26 10:00:00'),
       (5, '奶白夹棉短外套', '轻盈保暖不臃肿', '外套', 'AW', '聚酯纤维',
        '夹棉结构轻量保暖，奶白色在秋冬外套里更显干净。',
        'http://127.0.0.1:8081/seed/a7.jpg', 1440, 1975, 'ON', 110, 156, 52, '2026-11-05 10:00:00'),
       (6, '高领羊毛打底', '薄而暖的贴身层', '上装', 'AW', '羊毛',
        '细支羊毛针织，贴身穿不扎，适合叠穿在大衣里。',
        'http://127.0.0.1:8081/seed/a3.jpg', 1086, 1448, 'ON', 85, 74, 19, '2026-11-06 10:00:00'),
       (7, '直筒牛仔裤', '经典直筒，修饰腿型', '下装', 'AW', '棉',
        '中高腰直筒版型，硬挺牛仔布不易变形。',
        'http://127.0.0.1:8081/seed/a5.jpg', 1080, 1440, 'ON', 80, 61, 14, '2026-11-07 10:00:00'),
       (8, '黑色圆领针织衫', '一件穿三季', '上装', 'ALL', '棉',
        '细针织圆领，黑色基础款，单穿内搭都合适。',
        'http://127.0.0.1:8081/seed/a2.jpg', 1080, 1920, 'ON', 75, 92, 22, '2026-11-08 10:00:00'),
       (9, '拼色针织开衫', '一衣两色的层次感', '上装', 'AW', '羊毛混纺',
        '拼色织法自带层次，扣起来或敞穿都好看。',
        'http://127.0.0.1:8081/seed/a6.jpg', 1276, 1702, 'ON', 70, 118, 37, '2026-11-09 10:00:00'),
       (10, '水洗阔腿牛仔', '宽松阔腿，复古水洗', '下装', 'AW', '棉',
        '复古水洗蓝，阔腿版型走路带风。',
        'http://127.0.0.1:8081/seed/a1.jpg', 620, 828, 'ON', 65, 55, 12, '2026-11-10 10:00:00');

INSERT INTO product_image (product_id, url, width, height, sort_no)
VALUES (1, 'http://127.0.0.1:8081/seed/p-red.jpg', 1440, 1920, 0),
       (1, 'http://127.0.0.1:8081/seed/a6.jpg', 1276, 1702, 1),
       (1, 'http://127.0.0.1:8081/seed/a2.jpg', 1080, 1920, 2),
       (2, 'http://127.0.0.1:8081/seed/p-gray.jpg', 1080, 1440, 0),
       (2, 'http://127.0.0.1:8081/seed/a9.jpg', 1440, 1920, 1),
       (2, 'http://127.0.0.1:8081/seed/a5.jpg', 1080, 1440, 2),
       (3, 'http://127.0.0.1:8081/seed/p-hoodie.jpg', 1440, 1834, 0),
       (3, 'http://127.0.0.1:8081/seed/p-group.jpg', 1280, 2276, 1),
       (3, 'http://127.0.0.1:8081/seed/a7.jpg', 1440, 1975, 2),
       (4, 'http://127.0.0.1:8081/seed/p-sweat.jpg', 1280, 1566, 0),
       (4, 'http://127.0.0.1:8081/seed/a8.jpg', 1080, 1920, 1),
       (4, 'http://127.0.0.1:8081/seed/a4.jpg', 1280, 1760, 2),
       (5, 'http://127.0.0.1:8081/seed/a7.jpg', 1440, 1975, 0),
       (5, 'http://127.0.0.1:8081/seed/p-group.jpg', 1280, 2276, 1),
       (5, 'http://127.0.0.1:8081/seed/a9.jpg', 1440, 1920, 2),
       (6, 'http://127.0.0.1:8081/seed/a3.jpg', 1086, 1448, 0),
       (6, 'http://127.0.0.1:8081/seed/a6.jpg', 1276, 1702, 1),
       (6, 'http://127.0.0.1:8081/seed/p-sweat.jpg', 1280, 1566, 2),
       (7, 'http://127.0.0.1:8081/seed/a5.jpg', 1080, 1440, 0),
       (7, 'http://127.0.0.1:8081/seed/a1.jpg', 620, 828, 1),
       (7, 'http://127.0.0.1:8081/seed/p-jeans.jpg', 1280, 1708, 2),
       (8, 'http://127.0.0.1:8081/seed/a2.jpg', 1080, 1920, 0),
       (8, 'http://127.0.0.1:8081/seed/a8.jpg', 1080, 1920, 1),
       (8, 'http://127.0.0.1:8081/seed/a6.jpg', 1276, 1702, 2),
       (9, 'http://127.0.0.1:8081/seed/a6.jpg', 1276, 1702, 0),
       (9, 'http://127.0.0.1:8081/seed/a2.jpg', 1080, 1920, 1),
       (9, 'http://127.0.0.1:8081/seed/p-red.jpg', 1440, 1920, 2),
       (10, 'http://127.0.0.1:8081/seed/a1.jpg', 620, 828, 0),
       (10, 'http://127.0.0.1:8081/seed/a5.jpg', 1080, 1440, 1),
       (10, 'http://127.0.0.1:8081/seed/p-jeans.jpg', 1280, 1708, 2);

-- 每款 2 色 x 3 尺码，同款同价；origin 为 0 表示无原价
INSERT INTO sku (product_id, color, size, price_cent, origin_price_cent, sort_no)
SELECT v.pid, IF(c.ci = 1, v.c1, v.c2), s.size, v.price, v.origin, c.ci * 10 + s.no
FROM (VALUES ROW(1, 46900, 59900, '燕麦', '雾白'),
             ROW(2, 32900, 0, '茶褐', '浅卡其'),
             ROW(3, 89900, 119900, '焦糖棕', '深咖'),
             ROW(4, 23900, 0, '奶油白', '燕麦'),
             ROW(5, 69900, 89900, '奶白', '浅灰'),
             ROW(6, 32900, 0, '燕麦', '黑'),
             ROW(7, 39900, 0, '深蓝', '浅蓝'),
             ROW(8, 25900, 0, '黑', '炭灰'),
             ROW(9, 45900, 0, '拼色', '象牙白'),
             ROW(10, 36900, 0, '水洗蓝', '深蓝')) AS v(pid, price, origin, c1, c2)
         CROSS JOIN (SELECT 1 AS ci UNION ALL SELECT 2) c
         CROSS JOIN (SELECT 'S' AS size, 1 AS no UNION ALL SELECT 'M', 2 UNION ALL SELECT 'L', 3) s
ORDER BY v.pid, c.ci, s.no;

INSERT INTO inventory (store_id, sku_id, quantity, on_sale)
SELECT st.store_id, k.id, (k.id * 7 + st.store_id * 5) % 5, 1
FROM sku k
         CROSS JOIN (SELECT 1 AS store_id UNION ALL SELECT 2 UNION ALL SELECT 3) st;

-- 罗湖店：3 号外套整款售罄（详情页调货入口）、5 号夹棉总库存 2 件（低库存）
UPDATE inventory i
    JOIN sku k ON k.id = i.sku_id
SET i.quantity = 0
WHERE i.store_id = 1
  AND k.product_id = 3;

UPDATE inventory i
    JOIN sku k ON k.id = i.sku_id
SET i.quantity = CASE
                      WHEN k.color = '奶白' AND k.size = 'S' THEN 1
                      WHEN k.color = '浅灰' AND k.size = 'S' THEN 1
                      ELSE 0 END
WHERE i.store_id = 1
  AND k.product_id = 5;

-- 盐田店：6 号打底售罄，验证切店后库存变化
UPDATE inventory i
    JOIN sku k ON k.id = i.sku_id
SET i.quantity = 0
WHERE i.store_id = 3
  AND k.product_id = 6;

INSERT INTO collection (id, name, cover_url, story, publish_at, status, sort_no)
VALUES (1, 'AW26 秋冬系列',
        'http://127.0.0.1:8081/seed/p-group.jpg',
        '这一季我们回到羊毛与大地色，做能穿很多年的日常单品。',
        '2026-10-01 10:00:00', 'ON', 1);

INSERT INTO collection_product (collection_id, product_id, sort_no)
SELECT 1, id, id FROM product ORDER BY id;

INSERT INTO look (id, title, description, cover_url, cover_width, cover_height, status, sort_no, like_count,
                  fav_count)
VALUES (1, '焦糖棕 · 通勤一层层',
        '短外套 + 高领打底 + 直筒西裤，秋冬通勤最稳的一套。',
        'http://127.0.0.1:8081/seed/a9.jpg', 1440, 1920, 'ON', 1, 64, 21),
       (2, '奶白 · 周末轻出行',
        '夹棉短外套配牛仔，轻便保暖，适合周末逛街。',
        'http://127.0.0.1:8081/seed/a7.jpg', 1440, 1975, 'ON', 2, 48, 17),
       (3, '燕麦针织 · 日常叠穿',
        '开衫 + 牛仔 + 黑针织，三件就能换出很多组合。',
        'http://127.0.0.1:8081/seed/a6.jpg', 1276, 1702, 'ON', 3, 39, 12);

INSERT INTO look_item (look_id, product_id, sort_no)
VALUES (1, 3, 1), (1, 4, 2), (1, 2, 3),
       (2, 5, 1), (2, 7, 2), (2, 10, 3),
       (3, 1, 1), (3, 2, 2), (3, 8, 3);

INSERT INTO home_theme (id, name, cover_url, copy_text, start_at, end_at, status, is_current)
VALUES (1, 'WINTER 2026',
        'http://127.0.0.1:8081/seed/hero.jpg',
        'COLLECTION 01 · 三店同步上新',
        '2026-10-01 00:00:00', '2027-02-28 23:59:59', 'ON', 1);

INSERT INTO home_module (theme_id, type, ref_id, image_url, sort_no, enabled)
VALUES (1, 'BANNER', NULL, 'http://127.0.0.1:8081/seed/hero.jpg', 1, 1),
       (1, 'RECOMMEND', NULL, '', 2, 1),
       (1, 'COLLECTION', 1, 'http://127.0.0.1:8081/seed/p-group.jpg', 3, 1),
       (1, 'LOOK', 1, 'http://127.0.0.1:8081/seed/a9.jpg', 4, 1),
       (1, 'STORE', 1, '', 5, 1);
