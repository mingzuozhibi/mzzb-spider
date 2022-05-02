#!/usr/bin/env bash

echo "Sync remote db to local db"
ssh q 'bash admin/manual/gzip_mzzb_spider.sh' |
    gzip -d | mysql -uroot -p$DB_PASS mzzb_spider
echo "Sync done"
