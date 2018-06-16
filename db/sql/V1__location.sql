create table if not exists `location` 
(
`id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
`user_id` INTEGER NOT NULL,
`chat_id` INTEGER NOT NULL,
`longitude` DOUBLE NOT NULL,
`latitude` DOUBLE NOT NULL,
`enable` BOOLEAN NOT NULL,
`schedule` VARCHAR(254) NOT NULL,
`name` TEXT NOT NULL
);
