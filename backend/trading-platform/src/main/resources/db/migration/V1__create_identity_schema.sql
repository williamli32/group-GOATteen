-- Users table
CREATE TABLE users (

    id BIGSERIAL PRIMARY KEY,

    email VARCHAR(255) NOT NULL UNIQUE,

    password_hash VARCHAR(255) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);



-- Client profile

CREATE TABLE clients (

    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL UNIQUE,

    first_name VARCHAR(100) NOT NULL,

    last_name VARCHAR(100) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,


    CONSTRAINT fk_client_user
        FOREIGN KEY(user_id)
        REFERENCES users(id)
);



-- Trading account

CREATE TABLE accounts (

    id BIGSERIAL PRIMARY KEY,

    client_id BIGINT NOT NULL UNIQUE,

    account_number VARCHAR(50) NOT NULL UNIQUE,

    cash_balance NUMERIC(18,2) NOT NULL DEFAULT 0,

    currency VARCHAR(10) NOT NULL DEFAULT 'GBP',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,


    CONSTRAINT fk_account_client
        FOREIGN KEY(client_id)
        REFERENCES clients(id)
);



-- Roles

CREATE TABLE roles (

    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(50) NOT NULL UNIQUE
);



-- User roles

CREATE TABLE user_roles (

    user_id BIGINT NOT NULL,

    role_id BIGINT NOT NULL,


    PRIMARY KEY(user_id, role_id),


    CONSTRAINT fk_user_roles_user
        FOREIGN KEY(user_id)
        REFERENCES users(id),


    CONSTRAINT fk_user_roles_role
        FOREIGN KEY(role_id)
        REFERENCES roles(id)
);



-- Initial roles

INSERT INTO roles(name)
VALUES
('CLIENT'),
('OPERATIONS'),
('RISK_COMPLIANCE'),
('ANALYST'),
('ADMIN');