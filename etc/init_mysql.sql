DROP DATABASE IF EXISTS mzzb_spider;
DROP USER IF EXISTS 'mzzb_spider'@'localhost';

CREATE DATABASE mzzb_spider CHARSET utf8;
CREATE USER 'mzzb_spider'@'localhost' IDENTIFIED BY 'mzzb_spider';
GRANT ALL PRIVILEGES ON mzzb_spider.* TO 'mzzb_spider'@'localhost';
FLUSH PRIVILEGES;
