UPDATE users
SET role = 'OWNER'
WHERE role = 'AANBIEDER';

UPDATE users
SET role = 'RENTER'
WHERE role = 'HUURDER';