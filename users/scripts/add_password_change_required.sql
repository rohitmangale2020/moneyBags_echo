-- Apply once to the Oracle database used by users-service before deploying this feature.
-- Existing accounts remain able to sign in without a forced password change.
-- Every newly created account and every administrator password reset requires a change.
ALTER TABLE MB_USERS ADD (PASSWORD_CHANGE_REQUIRED NUMBER(1) DEFAULT 0 NOT NULL);

ALTER TABLE MB_USERS ADD CONSTRAINT CK_MB_USERS_PASSWORD_CHANGE_REQUIRED
    CHECK (PASSWORD_CHANGE_REQUIRED IN (0, 1));
