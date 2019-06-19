create table if not exists `user`
(
`id` INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
`user_id` INTEGER NOT NULL,
`language` INTEGER NOT NULL DEFAULT 0
);
