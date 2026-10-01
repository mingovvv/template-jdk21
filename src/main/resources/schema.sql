-- 스키마 단일 원본. Hibernate는 DDL을 생성하지 않음(ddl-auto=none, ADR 0009).
CREATE TABLE IF NOT EXISTS orders (
    id         UUID         PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    quantity   INT          NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);
