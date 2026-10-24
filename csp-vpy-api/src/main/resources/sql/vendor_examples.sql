-- CSP page 1 examples (PDF pages 7-8).
-- API-1 shows VENDOR_ID=101. IDs are now assigned in code order so new vendor
-- IDs stay sequential; this seed therefore does not preserve that example ID.
-- API-1 only supplies ****1234, not the full account or currency. Its stored
-- account 000000001234 and TWD are test-only values approved for this seed.
-- The API-1 response says TOTAL=2 but lists only one vendor; this seed does not
-- invent an additional matching vendor to reproduce that inconsistent count.
USE csp_db;
START TRANSACTION;

INSERT INTO VENDOR
    (VENDOR_CODE, TAX_ID, VENDOR_NAME, STATUS, ADDRESS, EMAIL, PHONE)
SELECT 'V0001', '12345678', '甲乙丙股份有限公司', 'ACTIVE',
       '台北市信義區信義路一段1號', 'contact@kanroji-ltd.com', '02-0336-0055'
WHERE NOT EXISTS (
    SELECT 1 FROM VENDOR
    WHERE VENDOR_CODE = 'V0001' OR TAX_ID = '12345678'
);

INSERT INTO VENDOR_BANK_ACCOUNT
    (VENDOR_ID, BANK_CODE, BANK_NAME, ACCOUNT_NAME, ACCOUNT_NUMBER, ACCOUNT_CCY, IS_DEFAULT)
SELECT v.VENDOR_ID, '808', '玉山銀行', '甲乙丙股份有限公司',
       '000000001234', 'TWD', TRUE
FROM VENDOR v
WHERE v.VENDOR_CODE = 'V0001' AND v.TAX_ID = '12345678'
  AND v.VENDOR_NAME = '甲乙丙股份有限公司'
  AND NOT EXISTS (
      SELECT 1 FROM VENDOR_BANK_ACCOUNT b
      WHERE b.VENDOR_ID = v.VENDOR_ID AND b.ACCOUNT_NUMBER = '000000001234'
  );

INSERT INTO VENDOR (VENDOR_CODE, TAX_ID, VENDOR_NAME, STATUS, ADDRESS, EMAIL, PHONE)
SELECT 'V0003', '53214567', '民安有限公司', 'ACTIVE',
       '台北市中山區OO路1號', 'ap@newuni.com', '02-12345678'
WHERE NOT EXISTS (
    SELECT 1 FROM VENDOR WHERE VENDOR_CODE = 'V0003' OR TAX_ID = '53214567'
);

INSERT INTO VENDOR_BANK_ACCOUNT
    (VENDOR_ID, BANK_CODE, BANK_NAME, ACCOUNT_NAME, ACCOUNT_NUMBER, ACCOUNT_CCY, IS_DEFAULT)
SELECT v.VENDOR_ID, '013', '國泰世華銀行', '民安有限公司', '123456789012', 'TWD', TRUE
FROM VENDOR v
WHERE v.VENDOR_CODE = 'V0003' AND v.TAX_ID = '53214567'
  AND NOT EXISTS (
      SELECT 1 FROM VENDOR_BANK_ACCOUNT b
      WHERE b.VENDOR_ID = v.VENDOR_ID AND b.ACCOUNT_NUMBER = '123456789012'
  );

COMMIT;
