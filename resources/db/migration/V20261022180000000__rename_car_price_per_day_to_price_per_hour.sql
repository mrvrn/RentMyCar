-- Keep existing car prices while aligning the schema with Cars.pricePerHour.
SET @rename_car_price := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'cars'
              AND COLUMN_NAME = 'pricePerDay'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'cars'
              AND COLUMN_NAME = 'pricePerHour'
        ),
        'ALTER TABLE cars RENAME COLUMN pricePerDay TO pricePerHour',
        'DO 0'
    )
);
PREPARE rename_car_price FROM @rename_car_price;
EXECUTE rename_car_price;
DEALLOCATE PREPARE rename_car_price;
-- Keep existing car prices while aligning the schema with Cars.pricePerHour.
SET @rename_car_price := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'cars'
              AND COLUMN_NAME = 'pricePerDay'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'cars'
              AND COLUMN_NAME = 'pricePerHour'
        ),
        'ALTER TABLE cars RENAME COLUMN pricePerDay TO pricePerHour',
        'DO 0'
    )
);
PREPARE rename_car_price FROM @rename_car_price;
EXECUTE rename_car_price;
DEALLOCATE PREPARE rename_car_price;
