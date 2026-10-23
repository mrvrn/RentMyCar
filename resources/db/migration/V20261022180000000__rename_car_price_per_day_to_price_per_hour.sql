-- Cars.pricePerHour (Exposed) replaced pricePerDay; keep existing prices.
ALTER TABLE cars RENAME COLUMN pricePerDay TO pricePerHour;
