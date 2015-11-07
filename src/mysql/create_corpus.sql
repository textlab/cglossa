INSERT IGNORE INTO `glossa__core`.`corpora` SET `code` = '{{corpus}}';

CREATE database IF NOT EXISTS `glossa_{{corpus}}` CHARACTER SET utf8 COLLATE utf8_unicode_ci;

USE glossa_{{corpus}};

CREATE TABLE IF NOT EXISTS `texts` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`startpos` bigint DEFAULT NULL,
`endpos` bigint DEFAULT NULL,
`bounds` text
);

CREATE TABLE IF NOT EXISTS `metadata_categories` (
`id` smallint unsigned NOT NULL AUTO_INCREMENT KEY,
`code` varchar(255) UNIQUE NOT NULL,
`name` varchar(255)
);

CREATE TABLE IF NOT EXISTS `metadata_values` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`metadata_category_id` smallint unsigned NOT NULL,
`type` enum('text', 'integer', 'boolean') NOT NULL,
`text_value` text,
`integer_value` int DEFAULT NULL,
`boolean_value` bool DEFAULT NULL,
KEY (`metadata_category_id`)
);

CREATE TABLE IF NOT EXISTS `metadata_values_texts` (
`metadata_value_id` int unsigned NOT NULL,
`text_id` int unsigned NOT NULL
);
