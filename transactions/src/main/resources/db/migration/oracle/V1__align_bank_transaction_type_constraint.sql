-- Hibernate does not replace an existing Oracle enum CHECK constraint when
-- Java enum values are added. Add the current constraint first, then remove
-- any older transaction_type IN constraint so the table is never unguarded.
DECLARE
    table_count NUMBER;
    current_constraint_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO table_count
      FROM user_tables
     WHERE table_name = 'BANK_TRANSACTION';

    IF table_count > 0 THEN
        SELECT COUNT(*)
          INTO current_constraint_count
          FROM user_constraints
         WHERE table_name = 'BANK_TRANSACTION'
           AND constraint_name = 'CK_BANK_TRANSACTION_TYPE';

        IF current_constraint_count = 0 THEN
            EXECUTE IMMEDIATE q'[
                ALTER TABLE bank_transaction ADD CONSTRAINT ck_bank_transaction_type CHECK (
                    transaction_type IN (
                        'TRANSFER',
                        'OPENING_DEPOSIT',
                        'DEPOSIT',
                        'WITHDRAWAL',
                        'MONTHLY_MAINTENANCE_FEE',
                        'ANNUAL_MAINTENANCE_FEE',
                        'INTEREST_CREDIT',
                        'FIXED_DEPOSIT_INTEREST_CREDIT',
                        'FIXED_DEPOSIT_FUNDING',
                        'FIXED_DEPOSIT_MATURITY',
                        'FIXED_DEPOSIT_PREMATURE_CLOSURE',
                        'PAYMENT',
                        'REVERSAL'
                    )
                ) ENABLE VALIDATE
            ]';
        END IF;

        FOR old_constraint IN (
            SELECT constraint_name
              FROM user_constraints
             WHERE table_name = 'BANK_TRANSACTION'
               AND constraint_type = 'C'
               AND constraint_name <> 'CK_BANK_TRANSACTION_TYPE'
               AND UPPER(search_condition_vc) LIKE '%TRANSACTION_TYPE%IN (%'
        ) LOOP
            EXECUTE IMMEDIATE 'ALTER TABLE bank_transaction DROP CONSTRAINT '
                    || DBMS_ASSERT.simple_sql_name(old_constraint.constraint_name);
        END LOOP;
    END IF;
END;
/
