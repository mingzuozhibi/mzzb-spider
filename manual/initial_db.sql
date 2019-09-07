# init mzzb_spider_pro

DROP DATABASE IF EXISTS mzzb_spider_pro;
DROP USER IF EXISTS 'mzzb_spider_pro'@'localhost';

CREATE DATABASE mzzb_spider_pro CHARSET utf8;
CREATE USER 'mzzb_spider_pro'@'localhost' IDENTIFIED BY 'mzzb_spider_pro';
GRANT ALL PRIVILEGES ON mzzb_spider_pro.* TO 'mzzb_spider_pro'@'localhost';
FLUSH PRIVILEGES;

# init mzzb_spider_dev

DROP DATABASE IF EXISTS mzzb_spider_dev;
DROP USER IF EXISTS 'mzzb_spider_dev'@'localhost';

CREATE DATABASE mzzb_spider_dev CHARSET utf8;
CREATE USER 'mzzb_spider_dev'@'localhost' IDENTIFIED BY 'mzzb_spider_dev';
GRANT ALL PRIVILEGES ON mzzb_spider_dev.* TO 'mzzb_spider_dev'@'localhost';
FLUSH PRIVILEGES;
