-- Hibernate does not replace an existing Oracle enum CHECK constraint when
-- Java enum values are added. Add the current constraint first, then remove
-- any older adjustment_type IN constraint so the table is never unguarded.
DECLARE
    table_count NUMBER;
    current_constraint_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO table_count
      FROM user_tables
     WHERE table_name = 'ACCOUNT_BALANCE_OPERATION';

    IF table_count > 0 THEN
        SELECT COUNT(*)
          INTO current_constraint_count
          FROM user_constraints
         WHERE table_name = 'ACCOUNT_BALANCE_OPERATION'
           AND constraint_name = 'CK_ACCOUNT_BALANCE_ADJUSTMENT_TYPE';

        IF current_constraint_count = 0 THEN
            EXECUTE IMMEDIATE q'[
                ALTER TABLE account_balance_operation
                    ADD CONSTRAINT ck_account_balance_adjustment_type CHECK (
                        adjustment_type IN (
                            'OPENING_DEPOSIT',
                            'DEPOSIT',
                            'WITHDRAWAL',
                            'MONTHLY_MAINTENANCE_FEE',
                            'ANNUAL_MAINTENANCE_FEE',
                            'INTEREST_CREDIT',
                            'FIXED_DEPOSIT_INTEREST_CREDIT'
                        )
                    ) ENABLE VALIDATE
            ]';
        END IF;

        FOR old_constraint IN (
            SELECT constraint_name
              FROM user_constraints
             WHERE table_name = 'ACCOUNT_BALANCE_OPERATION'
               AND constraint_type = 'C'
               AND constraint_name <> 'CK_ACCOUNT_BALANCE_ADJUSTMENT_TYPE'
               AND UPPER(search_condition_vc) LIKE '%ADJUSTMENT_TYPE%IN (%'
        ) LOOP
            EXECUTE IMMEDIATE 'ALTER TABLE account_balance_operation DROP CONSTRAINT '
                    || DBMS_ASSERT.simple_sql_name(old_constraint.constraint_name);
        END LOOP;
    END IF;
END;
/
