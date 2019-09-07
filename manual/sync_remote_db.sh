#!/usr/bin/env bash

if [[ $# == 1 && $1 == "-p" ]]; then
    ssh q "sh admin/gzip_mzzb_spider.sh" | gzip -d | mysql -uroot -pfuhaiwei mzzb_spider_pro
else
    ssh q "sh admin/gzip_mzzb_spider.sh" | gzip -d | mysql -uroot -pfuhaiwei mzzb_spider_dev
fi