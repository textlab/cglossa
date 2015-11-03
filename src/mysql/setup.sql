CREATE DATABASE IF NOT EXISTS `glossa__core` CHARACTER SET utf8 COLLATE utf8_unicode_ci;

USE glossa__core;

CREATE TABLE IF NOT EXISTS `corpora` (
`id` smallint unsigned NOT NULL AUTO_INCREMENT KEY,
`code` varchar(255) NOT NULL,
`name` varchar(255),
`encoding` varchar(255) DEFAULT 'utf-8',
`logo` varchar(255),
`search_engine` varchar(255) DEFAULT 'cwb'
);

CREATE TABLE IF NOT EXISTS `searches` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`user_id` int unsigned NOT NULL,
`corpus_id` smallint unsigned NOT NULL,
`queries` text NOT NULL,
`metadata_value_ids` text
);
