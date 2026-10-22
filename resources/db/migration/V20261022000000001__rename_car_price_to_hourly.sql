-- Match the hourly pricing field used by the car model.
ALTER TABLE cars CHANGE COLUMN pricePerDay pricePerHour DECIMAL(10, 2) NOT NULL;
