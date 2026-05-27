-- UC17 research product upload/list with metadata, status, and versioning.
ALTER TABLE products
    ADD COLUMN group_id BIGINT NULL AFTER project_id,
    ADD COLUMN submitted_by_id BIGINT NULL AFTER group_id,
    ADD COLUMN product_type VARCHAR(30) NULL AFTER submitted_by_id,
    ADD COLUMN title VARCHAR(200) NULL AFTER product_type,
    ADD COLUMN description TEXT NULL AFTER title,
    MODIFY COLUMN file_url VARCHAR(500) NULL,
    ADD COLUMN file_name VARCHAR(255) NULL AFTER file_url,
    ADD COLUMN file_type VARCHAR(100) NULL AFTER file_name,
    ADD COLUMN file_size BIGINT NULL AFTER file_type,
    ADD COLUMN external_link VARCHAR(1000) NULL AFTER file_size,
    ADD COLUMN version INT NULL AFTER external_link,
    ADD COLUMN status VARCHAR(30) NULL AFTER version,
    ADD COLUMN submitted_at TIMESTAMP(6) NULL AFTER status;

UPDATE products
SET
    submitted_by_id = COALESCE(submitted_by_id, 1),
    product_type = COALESCE(product_type, 'OTHER'),
    title = COALESCE(title, name),
    version = COALESCE(version, 1),
    status = COALESCE(status, 'SUBMITTED'),
    submitted_at = COALESCE(submitted_at, created_at)
WHERE product_type IS NULL
   OR title IS NULL
   OR version IS NULL
   OR status IS NULL
   OR submitted_at IS NULL;

ALTER TABLE products
    MODIFY COLUMN submitted_by_id BIGINT NOT NULL,
    MODIFY COLUMN product_type VARCHAR(30) NOT NULL,
    MODIFY COLUMN title VARCHAR(200) NOT NULL,
    MODIFY COLUMN version INT NOT NULL,
    MODIFY COLUMN status VARCHAR(30) NOT NULL,
    MODIFY COLUMN submitted_at TIMESTAMP(6) NOT NULL,
    ADD CONSTRAINT fk_product_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    ADD CONSTRAINT fk_product_submitter FOREIGN KEY (submitted_by_id) REFERENCES users (id),
    ADD CONSTRAINT uk_product_group_version UNIQUE (project_id, group_id, product_type, version),
    ADD INDEX idx_products_group_id (group_id),
    ADD INDEX idx_products_submitter_id (submitted_by_id),
    ADD INDEX idx_products_type_version (project_id, group_id, submitted_by_id, product_type, version);
