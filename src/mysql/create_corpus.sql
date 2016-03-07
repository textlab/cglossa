INSERT IGNORE INTO `{{glossa_prefix}}__core`.`corpus` SET `code` = '{{corpus}}';

CREATE database IF NOT EXISTS `{{glossa_prefix}}_{{corpus}}` CHARACTER SET utf8 COLLATE utf8_unicode_ci;
GRANT ALL ON `{{glossa_prefix}}_{{corpus}}`.* TO `{{db_user}}`@`localhost`;

USE {{glossa_prefix}}_{{corpus}};

CREATE TABLE IF NOT EXISTS `text` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`startpos` bigint DEFAULT NULL,
`endpos` bigint DEFAULT NULL,
`bounds` text,
`language` text
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `metadata_category` (
`id` smallint unsigned NOT NULL AUTO_INCREMENT KEY,
`code` varchar(255) UNIQUE NOT NULL,
`name` varchar(255)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `metadata_value` (
`id` int unsigned NOT NULL AUTO_INCREMENT KEY,
`metadata_category_id` smallint unsigned NOT NULL,
`type` enum('text', 'integer', 'boolean') NOT NULL,
`text_value` text,
`integer_value` int DEFAULT NULL,
`boolean_value` bool DEFAULT NULL,
KEY (`metadata_category_id`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `metadata_value_text` (
`metadata_value_id` int unsigned NOT NULL,
`text_id` int unsigned NOT NULL
) ENGINE=InnoDB;
