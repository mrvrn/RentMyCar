UPDATE users u
INNER JOIN cars c ON c.ownerId = u.id
SET u.role = 'OWNER'
WHERE u.role <> 'OWNER';
