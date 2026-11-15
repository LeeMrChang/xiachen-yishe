-- 演示图片归位：历史 uploads/ 目录 -> dev/ 目录（仅本地/测试环境加载）

CREATE TEMPORARY TABLE seed_url_move (
    old_url VARCHAR(512) NOT NULL PRIMARY KEY,
    new_url VARCHAR(512) NOT NULL
);

INSERT INTO seed_url_move (old_url, new_url)
VALUES        ('https://img.xiachen.xyz/uploads/202611/edbada57f5594bd9a37dbf482c66e7d6.jpg', 'https://img.xiachen.xyz/dev/202611/2920f80815754b0982a0ec9d064da503.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/ffec96bf0a4142bab419666d0585307f.jpg', 'https://img.xiachen.xyz/dev/202611/29de605c983f4486b60ddebd8399cd07.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/6ccbbaa074bf431b9c2c28e657e061b6.jpg', 'https://img.xiachen.xyz/dev/202611/e28a7b17a162467589ff5d57b5c02b29.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/86b2032b5734430aa5354b0b8691f4a5.jpg', 'https://img.xiachen.xyz/dev/202611/058dfe4bd7044aa3bb5f56fe3f14af65.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/27c40ac905484f608936f7e132917e89.jpg', 'https://img.xiachen.xyz/dev/202611/a3e0c07a800e4670ad2cdd9c91946a21.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/7ca7556ed5f44dcfbeb3ec3f93e608b5.jpg', 'https://img.xiachen.xyz/dev/202611/7704b1f753c44a74b6ae4856e10b1249.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/f319f501a8dc4621a5dc387b3affb156.jpg', 'https://img.xiachen.xyz/dev/202611/beee8ca0ed6c4cce91fcdca9fbbdc915.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/26717ac4dfad4de5955f99210e96afbb.jpg', 'https://img.xiachen.xyz/dev/202611/e19ead0ae5e244fb9d8b6da6ab999587.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/f4de0a38cb6b4da88183feb06b20118b.jpg', 'https://img.xiachen.xyz/dev/202611/9f8a0261bafc4151816f1df10039ec5a.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/e6aae0f93a954c9b8295f5917ed7b027.jpg', 'https://img.xiachen.xyz/dev/202611/ab9158d73f7b47f39c09a2ba880f36c5.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/5545290208ae4321a86a0feaf85fa308.jpg', 'https://img.xiachen.xyz/dev/202611/ee98ac842ced46ec8effcc0f9df0f291.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/73bdaf6ca3e1424790f85be5c246b1fa.jpg', 'https://img.xiachen.xyz/dev/202611/4f070599d990413981e36acbf07bb11a.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/c7ca32c4dd6641f98fff6955431ebbc7.jpg', 'https://img.xiachen.xyz/dev/202611/387447e2ac6841b08ff2cc42ba9538a7.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/b2ca294d249d4e49ae85c1345d1248d9.jpg', 'https://img.xiachen.xyz/dev/202611/2a292d89739f4fb5a542afc6e549cc61.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/e986aabbf2d14718897f0f28a0e49be3.jpg', 'https://img.xiachen.xyz/dev/202611/8aca1f0acbb345709be972b7c051fd7f.jpg'),
       ('https://img.xiachen.xyz/uploads/202611/29e0d2550c4640fbba0283a88e653e4c.jpg', 'https://img.xiachen.xyz/dev/202611/69c61f29d211472d9e276390fcf55bcf.jpg');

UPDATE product p
    JOIN seed_url_move m ON p.cover_url = m.old_url
SET p.cover_url = m.new_url;

UPDATE product_image i
    JOIN seed_url_move m ON i.url = m.old_url
SET i.url = m.new_url;

UPDATE collection c
    JOIN seed_url_move m ON c.cover_url = m.old_url
SET c.cover_url = m.new_url;

UPDATE look l
    JOIN seed_url_move m ON l.cover_url = m.old_url
SET l.cover_url = m.new_url;

UPDATE home_theme t
    JOIN seed_url_move m ON t.cover_url = m.old_url
SET t.cover_url = m.new_url;

UPDATE home_module hm
    JOIN seed_url_move m ON hm.image_url = m.old_url
SET hm.image_url = m.new_url;

DROP TEMPORARY TABLE seed_url_move;
