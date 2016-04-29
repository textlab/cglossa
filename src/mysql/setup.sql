CREATE DATABASE IF NOT EXISTS `{{glossa_prefix}}__core` CHARACTER SET utf8 COLLATE utf8_unicode_ci;
GRANT ALL ON `{{glossa_prefix}}__core`.* TO `{{db_user}}`@`localhost` IDENTIFIED BY '{{db_password}}';
GRANT FILE ON *.* TO `{{db_user}}`@`localhost`;

USE {{glossa_prefix}}__core;

CREATE TABLE IF NOT EXISTS `corpus` (
`id` smallint unsigned NOT NULL AUTO_INCREMENT KEY,
`code` varchar(255) UNIQUE NOT NULL,
`name` varchar(255),
`encoding` varchar(255) DEFAULT 'utf-8',
`logo` varchar(255),
`search_engine` varchar(255) DEFAULT 'cwb',
`languages` text,
`multicpu_bounds` text
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `search` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`user_id` int unsigned NOT NULL,
`corpus_id` smallint unsigned NOT NULL,
`queries` text NOT NULL,
`metadata_value_ids` text
) ENGINE=InnoDB;
