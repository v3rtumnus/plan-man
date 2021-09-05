CREATE TABLE credit_single_transaction (
    id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    transaction_date date DEFAULT NULL,
    description varchar(255) DEFAULT NULL,
    amount decimal(10,2) DEFAULT NULL,
    UNIQUE KEY id (id)
);

CREATE TABLE user_profile (
    password varchar(255) NOT NULL,
    username varchar(255) NOT NULL,
    PRIMARY KEY (username)
);

CREATE TABLE expense_category (
    id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    name varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE expense (
    id bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    transaction_date date NOT NULL,
    comment varchar(255) DEFAULT NULL,
    amount decimal(10,2) NOT NULL,
    expense_category_id bigint(20) unsigned NOT NULL,
    user_profile_id varchar(255) NOT NULL,
    FOREIGN KEY (expense_category_id) REFERENCES expense_category(id),
    FOREIGN KEY (user_profile_id) REFERENCES user_profile(username),
    PRIMARY KEY (id)
);