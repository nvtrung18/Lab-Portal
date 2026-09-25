-- Remove deterministic demo rows created by historical shared migrations V2, V24, and V25.
-- Keep the application roles; ProductionAdminBootstrapper creates the configured production admin after Flyway.

DELETE FROM applications
WHERE cv_url IN (
    'https://example.com/cv/student01-ai.pdf',
    'https://example.com/cv/student02-ai.pdf',
    'https://example.com/cv/student03-robotics.pdf',
    'https://example.com/cv/student04-data.pdf',
    'https://example.com/cv/student05-cybersecurity.pdf',
    'https://example.com/cv/student01-iot.pdf',
    'https://example.com/cv/student02-robotics.pdf'
);

DELETE l
FROM laboratories l
JOIN users manager ON manager.id = l.manager_id
WHERE (l.lab_name, l.description) IN (
    ('AI Research Lab', 'Simple test lab for artificial intelligence and machine learning.'),
    ('Robotics Lab', 'Simple test lab for robotics, embedded systems, and automation.'),
    ('Data Science Lab', 'Simple test lab for analytics, visualization, and data engineering.'),
    ('Cybersecurity Lab', 'Simple test lab for security testing and network defense.'),
    ('IoT Innovation Lab', 'Simple test lab for IoT devices, sensors, and cloud prototypes.')
)
AND manager.username IN ('manager01', 'manager02', 'manager03', 'manager04', 'manager05')
AND manager.email = CONCAT(manager.username, '@labportal.com');

DELETE FROM users
WHERE (username, email) IN (
    ('admin', 'admin@labportal.com'),
    ('lab_manager', 'lab_manager@labportal.com'),
    ('user1', 'user1@labportal.com'),
    ('user2', 'user2@labportal.com'),
    ('user3', 'user3@labportal.com'),
    ('user4', 'user4@labportal.com'),
    ('manager01', 'manager01@labportal.com'),
    ('manager02', 'manager02@labportal.com'),
    ('manager03', 'manager03@labportal.com'),
    ('manager04', 'manager04@labportal.com'),
    ('manager05', 'manager05@labportal.com'),
    ('student01', 'student01@labportal.com'),
    ('student02', 'student02@labportal.com'),
    ('student03', 'student03@labportal.com'),
    ('student04', 'student04@labportal.com'),
    ('student05', 'student05@labportal.com')
);
