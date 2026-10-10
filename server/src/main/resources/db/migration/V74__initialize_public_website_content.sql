-- Keep existing local website content seeded by the historical V72 migration.
INSERT INTO system_configs (config_key, config_value_json, active, deleted, created_at, updated_at)
SELECT 'PUBLIC_WEBSITE_CONTENT',
       '{"revision":0,"draft":null,"published":null,"publishedAt":null}',
       TRUE, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM system_configs WHERE config_key = 'PUBLIC_WEBSITE_CONTENT'
);
