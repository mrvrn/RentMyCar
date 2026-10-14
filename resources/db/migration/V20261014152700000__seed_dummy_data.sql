-- Seed data for local development only
-- DO NOT use these passwords in production
INSERT INTO users (name, email, phone, address, password) VALUES 
('Test User', 'test@example.com', '1234567890', 'Test Address 1', '$2a$10$dummy.hash.for.dev.only'),
('Demo User', 'demo@example.com', '0987654321', 'Test Address 2', '$2a$10$dummy.hash.for.dev.only2');

-- Note: userId references assume auto-generated IDs 1 and 2 from above
INSERT INTO locations (streetName, houseNumber, postalCode, city, country, userId) VALUES 
('Main Street', '1', '1234AB', 'Amsterdam', 'Netherlands', 1),
('Side Street', '2', '5678CD', 'Rotterdam', 'Netherlands', 1),
('Central Avenue', '3', '8901EF', 'Utrecht', 'Netherlands', 2);
