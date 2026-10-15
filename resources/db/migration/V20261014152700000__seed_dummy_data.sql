-- Seed data for local development only
-- DO NOT use these passwords in production
INSERT INTO users (name, email, phone, address, password) VALUES 
('Test User', 'test@example.com', '1234567890', 'Test Address 1', '$2a$10$dummy.hash.for.dev.only'),
('Demo User', 'demo@example.com', '0987654321', 'Test Address 2', '$2a$10$dummy.hash.for.dev.only2');

-- Use email to lookup inserted user IDs (email is unique)
INSERT INTO locations (streetName, houseNumber, postalCode, city, country, userId) VALUES 
('Main Street', '1', '1234AB', 'Amsterdam', 'Netherlands', (SELECT id FROM users WHERE email = 'test@example.com')),
('Side Street', '2', '5678CD', 'Rotterdam', 'Netherlands', (SELECT id FROM users WHERE email = 'test@example.com')),
('Central Avenue', '3', '8901EF', 'Utrecht', 'Netherlands', (SELECT id FROM users WHERE email = 'demo@example.com'));
