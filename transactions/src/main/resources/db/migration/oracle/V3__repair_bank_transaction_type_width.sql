-- Reassert the current mapping for installations that were restarted from a
-- stale build after V2 and consequently restored the legacy 30-char width.
DECLARE
    table_count NUMBER;
    current_length NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO table_count
      FROM user_tables
     WHERE table_name = 'BANK_TRANSACTION';

    IF table_count > 0 THEN
        SELECT char_length
          INTO current_length
          FROM user_tab_columns
         WHERE table_name = 'BANK_TRANSACTION'
           AND column_name = 'TRANSACTION_TYPE';

        IF current_length < 40 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE bank_transaction MODIFY (transaction_type VARCHAR2(40 CHAR))';
        END IF;
    END IF;
END;
/
