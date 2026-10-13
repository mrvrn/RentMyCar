-- Preserve existing migration history and convert daily prices to cents precision.
ALTER TABLE cars MODIFY COLUMN pricePerDay DECIMAL(10, 2) NOT NULL;
