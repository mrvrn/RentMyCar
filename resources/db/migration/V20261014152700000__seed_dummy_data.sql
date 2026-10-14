INSERT INTO users (id, name, email, phone, address, password) VALUES 
(1, 'Test User', 'test@example.com', '1234567890', 'Test Address 1', 'hashedpassword123'),
(2, 'Demo User', 'demo@example.com', '0987654321', 'Test Address 2', 'hashedpassword456');

INSERT INTO locations (id, streetName, houseNumber, postalCode, city, country, userId) VALUES 
(1, 'Main Street', '1', '1234AB', 'Amsterdam', 'Netherlands', 1),
(2, 'Side Street', '2', '5678CD', 'Rotterdam', 'Netherlands', 1),
(3, 'Central Avenue', '3', '8901EF', 'Utrecht', 'Netherlands', 2);
