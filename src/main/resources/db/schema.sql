CREATE TABLE `credit_single_transaction` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `transaction_date` date DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  UNIQUE KEY `id` (`id`)
);

CREATE TABLE `user_profile` (
  `password` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  PRIMARY KEY (`username`)
);