CREATE database IF NOT EXISTS `glossa_{{corpus}}` CHARACTER SET utf8 COLLATE utf8_unicode_ci;

USE glossa_{{corpus}};

CREATE TABLE IF NOT EXISTS `texts` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`startpos` bigint DEFAULT NULL,
`endpos` bigint DEFAULT NULL,
`positions` text
);

CREATE TABLE IF NOT EXISTS `metadata_categories` (
`id` smallint unsigned NOT NULL AUTO_INCREMENT KEY,
`code` varchar(255) NOT NULL,
`name` varchar(255)
);

CREATE TABLE IF NOT EXISTS `metadata_values` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`metadata_category_id` smallint unsigned NOT NULL,
`type` enum('text', 'integer', 'boolean') NOT NULL,
`text_value` text,
`integer_value` int DEFAULT NULL,
`boolean_value` bool DEFAULT NULL
);
