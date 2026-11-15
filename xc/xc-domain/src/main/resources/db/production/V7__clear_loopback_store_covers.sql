-- 历史门店种子曾引用开发机图片；清除不可供真机访问的封面。
-- 不覆盖后台已经维护的真实图片，待运营上传门店实拍图。
UPDATE store
SET cover_url = ''
WHERE cover_url REGEXP '^https?://(127[.]0[.]0[.]1|localhost)(:[0-9]+)?(/|$)';
