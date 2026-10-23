-- Enforce unique license plates in the database. Existing duplicates make this fail on purpose,
-- so they are resolved manually instead of silently deleting cars.
SET @add_unique_license_plate := (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cars' AND INDEX_NAME = 'idx_unique_license_plate'),
        'DO 0',
        'ALTER TABLE cars ADD UNIQUE INDEX idx_unique_license_plate (licensePlate)'
    )
);
PREPARE add_unique_license_plate FROM @add_unique_license_plate;
EXECUTE add_unique_license_plate;
DEALLOCATE PREPARE add_unique_license_plate;
