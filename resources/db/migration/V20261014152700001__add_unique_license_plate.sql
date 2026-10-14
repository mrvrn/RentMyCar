-- Add unique constraint on licensePlate to prevent duplicate cars
ALTER TABLE cars ADD UNIQUE INDEX idx_unique_license_plate (licensePlate);
