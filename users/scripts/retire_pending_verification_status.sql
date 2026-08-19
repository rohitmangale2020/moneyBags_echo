-- Run before deploying the version that removes PENDING_VERIFICATION from UserStatus.
-- Existing pending platform users become active while retaining their temporary-password requirement.
UPDATE MB_USERS
SET STATUS = 'ACTIVE'
WHERE STATUS = 'PENDING_VERIFICATION';
