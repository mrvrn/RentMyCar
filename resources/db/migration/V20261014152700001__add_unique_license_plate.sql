-- Add unique constraint on licensePlate to prevent duplicate cars
-- First, remove any duplicate license plates (keep the first occurrence, delete others)
DELETE c1 FROM cars c1
INNER JOIN cars c2 
WHERE c1.id > c2.id 
AND c1.licensePlate = c2.licensePlate;

-- Now add the unique constraint
ALTER TABLE cars ADD UNIQUE INDEX idx_unique_license_plate (licensePlate);
