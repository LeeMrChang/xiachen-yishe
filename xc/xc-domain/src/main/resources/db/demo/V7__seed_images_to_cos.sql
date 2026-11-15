-- 演示数据图片迁移：本地 /seed/ 静态图 -> COS（img.xiachen.xyz）
-- 仅本地/测试环境加载；生产不执行本迁移

CREATE TEMPORARY TABLE seed_url_map (
    file_name VARCHAR(64) NOT NULL PRIMARY KEY,
    url       VARCHAR(512) NOT NULL
);

INSERT INTO seed_url_map (file_name, url)
VALUES ('a1.jpg', 'https://img.xiachen.xyz/uploads/202611/86b2032b5734430aa5354b0b8691f4a5.jpg'),
       ('a2.jpg', 'https://img.xiachen.xyz/uploads/202611/b2ca294d249d4e49ae85c1345d1248d9.jpg'),
       ('a3.jpg', 'https://img.xiachen.xyz/uploads/202611/27c40ac905484f608936f7e132917e89.jpg'),
       ('a4.jpg', 'https://img.xiachen.xyz/uploads/202611/26717ac4dfad4de5955f99210e96afbb.jpg'),
       ('a5.jpg', 'https://img.xiachen.xyz/uploads/202611/6ccbbaa074bf431b9c2c28e657e061b6.jpg'),
       ('a6.jpg', 'https://img.xiachen.xyz/uploads/202611/e986aabbf2d14718897f0f28a0e49be3.jpg'),
       ('a7.jpg', 'https://img.xiachen.xyz/uploads/202611/7ca7556ed5f44dcfbeb3ec3f93e608b5.jpg'),
       ('a8.jpg', 'https://img.xiachen.xyz/uploads/202611/5545290208ae4321a86a0feaf85fa308.jpg'),
       ('a9.jpg', 'https://img.xiachen.xyz/uploads/202611/c7ca32c4dd6641f98fff6955431ebbc7.jpg'),
       ('hero.jpg', 'https://img.xiachen.xyz/uploads/202611/29e0d2550c4640fbba0283a88e653e4c.jpg'),
       ('p-gray.jpg', 'https://img.xiachen.xyz/uploads/202611/e6aae0f93a954c9b8295f5917ed7b027.jpg'),
       ('p-group.jpg', 'https://img.xiachen.xyz/uploads/202611/edbada57f5594bd9a37dbf482c66e7d6.jpg'),
       ('p-hoodie.jpg', 'https://img.xiachen.xyz/uploads/202611/73bdaf6ca3e1424790f85be5c246b1fa.jpg'),
       ('p-jeans.jpg', 'https://img.xiachen.xyz/uploads/202611/ffec96bf0a4142bab419666d0585307f.jpg'),
       ('p-red.jpg', 'https://img.xiachen.xyz/uploads/202611/f4de0a38cb6b4da88183feb06b20118b.jpg'),
       ('p-sweat.jpg', 'https://img.xiachen.xyz/uploads/202611/f319f501a8dc4621a5dc387b3affb156.jpg');

UPDATE product p
    JOIN seed_url_map m ON p.cover_url = CONCAT('http://127.0.0.1:8081/seed/', m.file_name)
SET p.cover_url = m.url;

UPDATE product_image i
    JOIN seed_url_map m ON i.url = CONCAT('http://127.0.0.1:8081/seed/', m.file_name)
SET i.url = m.url;

UPDATE collection c
    JOIN seed_url_map m ON c.cover_url = CONCAT('http://127.0.0.1:8081/seed/', m.file_name)
SET c.cover_url = m.url;

UPDATE look l
    JOIN seed_url_map m ON l.cover_url = CONCAT('http://127.0.0.1:8081/seed/', m.file_name)
SET l.cover_url = m.url;

UPDATE home_theme t
    JOIN seed_url_map m ON t.cover_url = CONCAT('http://127.0.0.1:8081/seed/', m.file_name)
SET t.cover_url = m.url;

UPDATE home_module hm
    JOIN seed_url_map m ON hm.image_url = CONCAT('http://127.0.0.1:8081/seed/', m.file_name)
SET hm.image_url = m.url;

DROP TEMPORARY TABLE seed_url_map;
