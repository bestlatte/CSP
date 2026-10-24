package com.cathaybk.csp.service;

import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class CspService {

    private static final Logger LOG = LoggerFactory.getLogger(CspService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final DateTimeFormatter INVOICE_PERIOD = DateTimeFormatter.ofPattern("uuuuMM", Locale.ROOT);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public CspService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 範例程式
     *
     * @param demoMap
     * @return
     */
    public Map<String, Object> demoCode(Map<String, Object> demoMap) {
        /* 1. 將前端傳入值取出：使用前端傳入物件的key值，從Map中取得對應value，例如：*/
        String id = demoMap.get("id").toString();
        String keyword = demoMap.get("keyword").toString();
        System.err.println("id--->" + id);
        System.err.println("keyword--->" + keyword);

        /* 2. 業務邏輯：檢核、題目要求邏輯實作
        try (關鍵字：JDBC、try-with-resource) {

        } catch (SQLException e) {
            e.printStackTrace();
        }
        */

        /* 3. 把要回傳給前端的值包裝成Map後return，例如： */
        Map<String, Object> rtnMap = new HashMap<>();
        Map<String, Object> mwHeader = new HashMap<>();
        mwHeader.put("MSGID", "DEMO001");
        rtnMap.put("MWHEADER", mwHeader);
        rtnMap.put("TRANRQ", null);
        return rtnMap;
    }

    /**
     * API-1：廠商查詢（模糊條件）
     *
     * @param map
     * @return
     */
    public Map<String, Object> queryVendor(Map<String, Object> map) {
        try {
            Map<String, Object> request = request(map);
            String taxId = optionalString(request, "TAX_ID", 8);
            String name = optionalString(request, "VENDOR_NAME", 200);
            String status = optionalString(request, "STATUS", 10);
            if (status != null && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw invalid("STATUS 必須為 ACTIVE 或 INACTIVE");
            }

            StringBuilder sql = new StringBuilder("SELECT VENDOR_ID, VENDOR_CODE, TAX_ID, VENDOR_NAME, STATUS, "
                    + "ADDRESS, EMAIL, PHONE FROM VENDOR WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (taxId != null) {
                sql.append(" AND TAX_ID LIKE ? ESCAPE '!'");
                args.add("%" + escapeLike(taxId) + "%");
            }
            if (name != null) {
                sql.append(" AND VENDOR_NAME LIKE ? ESCAPE '!'");
                args.add("%" + escapeLike(name) + "%");
            }
            if (status != null) {
                sql.append(" AND STATUS = ?");
                args.add(status);
            }
            sql.append(" ORDER BY VENDOR_CODE, VENDOR_ID");
            List<Map<String, Object>> vendors = jdbc.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
                Map<String, Object> vendor = new LinkedHashMap<>();
                vendor.put("VENDOR_ID", rs.getLong("VENDOR_ID"));
                vendor.put("VENDOR_CODE", rs.getString("VENDOR_CODE"));
                vendor.put("TAX_ID", rs.getString("TAX_ID"));
                vendor.put("VENDOR_NAME", rs.getString("VENDOR_NAME"));
                vendor.put("STATUS", rs.getString("STATUS"));
                vendor.put("ADDRESS", rs.getString("ADDRESS"));
                vendor.put("EMAIL", rs.getString("EMAIL"));
                vendor.put("PHONE", rs.getString("PHONE"));
                return vendor;
            });

            if (!vendors.isEmpty()) {
                StringBuilder bankSql = new StringBuilder("SELECT ACCOUNT_ID, VENDOR_ID, BANK_CODE, BANK_NAME, "
                        + "ACCOUNT_NAME, ACCOUNT_NUMBER, ACCOUNT_CCY, IS_DEFAULT FROM VENDOR_BANK_ACCOUNT "
                        + "WHERE VENDOR_ID IN (");
                List<Object> ids = new ArrayList<>();
                Map<Long, Map<String, Object>> byId = new HashMap<>();
                for (Map<String, Object> vendor : vendors) {
                    if (!ids.isEmpty()) bankSql.append(',');
                    bankSql.append('?');
                    Long id = (Long) vendor.get("VENDOR_ID");
                    ids.add(id);
                    byId.put(id, vendor);
                    vendor.put("BANK_ACCOUNTS", new ArrayList<Map<String, Object>>());
                }
                bankSql.append(") ORDER BY VENDOR_ID, IS_DEFAULT DESC, ACCOUNT_ID");
                jdbc.query(bankSql.toString(), ids.toArray(), rs -> {
                    Map<String, Object> vendor = byId.get(rs.getLong("VENDOR_ID"));
                    if (vendor == null) return;
                    Map<String, Object> bank = bankResponse(rs.getLong("ACCOUNT_ID"),
                            rs.getString("BANK_CODE"), rs.getString("BANK_NAME"),
                            rs.getString("ACCOUNT_NAME"), rs.getString("ACCOUNT_NUMBER"),
                            rs.getString("ACCOUNT_CCY"), rs.getBoolean("IS_DEFAULT"));
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> accounts = (List<Map<String, Object>>) vendor.get("BANK_ACCOUNTS");
                    accounts.add(bank);
                    if (Boolean.TRUE.equals(bank.get("IS_DEFAULT"))) {
                        Map<String, Object> primary = new LinkedHashMap<>(bank);
                        primary.remove("ACCOUNT_ID");
                        primary.remove("IS_DEFAULT");
                        vendor.put("BANK", primary);
                    }
                });
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("TOTAL", vendors.size());
            result.put("VENDORS", vendors);
            return response(map, "VDRQRY01", "0000", "交易成功", result);
        } catch (BusinessException e) {
            return response(map, "VDRQRY01", e.code, e.getMessage(), Map.of());
        } catch (DataAccessException e) {
            LOG.error("Vendor query failed", e);
            return response(map, "VDRQRY01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Vendor query failed", e);
            return response(map, "VDRQRY01", "9999", "系統錯誤", Map.of());
        }
    }

    /**
     * API-2：廠商新增 / 更新
     *
     * @param map
     * @return
     */
    public Map<String, Object> upsertVendor(Map<String, Object> map) {
        try {
            Map<String, Object> request = request(map);
            Map<String, Object> vendor = requiredMap(request, "VENDOR");
            Long vendorId = optionalId(vendor, "VENDOR_ID");
            String vendorCode = optionalString(vendor, "VENDOR_CODE", 20);
            String taxId = requiredString(vendor, "TAX_ID", 8);
            if (!taxId.matches("[0-9]{8}")) throw invalid("TAX_ID 必須為 8 位數字");
            String vendorName = requiredString(vendor, "VENDOR_NAME", 200);
            String status = requiredString(vendor, "STATUS", 10);
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw invalid("STATUS 必須為 ACTIVE 或 INACTIVE");
            }
            String address = optionalString(vendor, "ADDRESS", 500);
            String email = optionalString(vendor, "EMAIL", 255);
            String phone = optionalString(vendor, "PHONE", 30);
            if (email != null && !EMAIL.matcher(email).matches()) throw invalid("EMAIL 格式錯誤");
            boolean hasBank = vendor.get("BANK") != null;
            boolean hasAccounts = vendor.get("BANK_ACCOUNTS") != null;
            if (hasBank == hasAccounts) throw invalid("必須提供 BANK 或 BANK_ACCOUNTS，且不可同時提供");

            transaction.executeWithoutResult(ignored -> {
                long id;
                if (vendorId == null) {
                    String code = vendorCode == null ? nextVendorCode() : vendorCode;
                    KeyHolder key = new GeneratedKeyHolder();
                    jdbc.update(con -> {
                        PreparedStatement ps = con.prepareStatement("INSERT INTO VENDOR "
                                + "(VENDOR_CODE, TAX_ID, VENDOR_NAME, STATUS, ADDRESS, EMAIL, PHONE) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)", new String[] { "VENDOR_ID" });
                        ps.setString(1, code);
                        ps.setString(2, taxId);
                        ps.setString(3, vendorName);
                        ps.setString(4, status);
                        ps.setString(5, address);
                        ps.setString(6, email);
                        ps.setString(7, phone);
                        return ps;
                    }, key);
                    if (key.getKey() == null) throw new IllegalStateException("Missing VENDOR_ID");
                    id = key.getKey().longValue();
                } else {
                    id = vendorId;
                    List<String> codes = jdbc.query("SELECT VENDOR_CODE FROM VENDOR WHERE VENDOR_ID = ? FOR UPDATE",
                            (rs, rowNum) -> rs.getString(1), id);
                    if (codes.isEmpty()) throw new BusinessException("E702", "查無資料");
                    if (vendorCode != null && !vendorCode.equals(codes.get(0))) {
                        throw invalid("VENDOR_CODE 不可修改");
                    }
                    jdbc.update("UPDATE VENDOR SET TAX_ID = ?, VENDOR_NAME = ?, STATUS = ?, ADDRESS = ?, EMAIL = ?, PHONE = ? "
                            + "WHERE VENDOR_ID = ?", taxId, vendorName, status, address, email, phone, id);
                }

                if (hasBank) {
                    saveSingleBank(id, requiredMap(vendor, "BANK"), vendorId == null);
                } else {
                    saveBankAccounts(id, requiredList(vendor, "BANK_ACCOUNTS"), vendorId == null);
                }
            });
            return response(map, "VDRUPS01", "0000", "交易成功", Map.of());
        } catch (BusinessException e) {
            return response(map, "VDRUPS01", e.code, e.getMessage(), Map.of());
        } catch (DataIntegrityViolationException e) {
            return response(map, "VDRUPS01", "E001", "廠商代碼、統編或銀行資料重複／格式錯誤", Map.of());
        } catch (DataAccessException e) {
            LOG.error("Vendor upsert failed", e);
            return response(map, "VDRUPS01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Vendor upsert failed", e);
            return response(map, "VDRUPS01", "9999", "系統錯誤", Map.of());
        }
    }

    private String nextVendorCode() {
        Long lastNumber = jdbc.queryForObject(
                "SELECT LAST_NUMBER FROM VENDOR_CODE_COUNTER WHERE COUNTER_NAME = 'VENDOR' FOR UPDATE",
                Long.class);
        Long largestCode = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(VENDOR_CODE, 2) AS UNSIGNED)), 0) "
                        + "FROM VENDOR WHERE VENDOR_CODE REGEXP '^V[0-9]+$'",
                Long.class);
        long next = Math.addExact(Math.max(lastNumber, largestCode), 1);
        String code = String.format(Locale.ROOT, "V%04d", next);
        if (code.length() > 20) throw invalid("VENDOR_CODE 編號已達上限");
        jdbc.update("UPDATE VENDOR_CODE_COUNTER SET LAST_NUMBER = ? WHERE COUNTER_NAME = 'VENDOR'", next);
        return code;
    }

    /**
     * API-3：取得下拉選單（廠商 / 稅率 / 幣別）
     *
     * @param map
     * @return
     */
    public Map<String, Object> queryOptions(Map<String, Object> map) {
        try {
            request(map);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("CURRENCIES", jdbc.query(
                    "SELECT CURRENCY_CODE, CURRENCY_NAME FROM CURRENCY ORDER BY DISPLAY_ORDER, CURRENCY_CODE",
                    (rs, rowNum) -> {
                        Map<String, Object> currency = new LinkedHashMap<>();
                        currency.put("CODE", rs.getString("CURRENCY_CODE"));
                        currency.put("NAME", rs.getString("CURRENCY_NAME"));
                        return currency;
                    }));
            result.put("TAX_RATES", jdbc.query(
                    "SELECT TAX_CODE, PERCENT FROM TAX_RATE ORDER BY DISPLAY_ORDER, TAX_CODE",
                    (rs, rowNum) -> {
                        Map<String, Object> tax = new LinkedHashMap<>();
                        tax.put("CODE", rs.getString("TAX_CODE"));
                        tax.put("PERCENT", rs.getBigDecimal("PERCENT"));
                        return tax;
                    }));
            List<Map<String, Object>> vendors = jdbc.query(
                    "SELECT VENDOR_ID, VENDOR_CODE, VENDOR_NAME FROM VENDOR WHERE STATUS = 'ACTIVE' ORDER BY VENDOR_NAME",
                    (rs, rowNum) -> {
                        Map<String, Object> vendor = new LinkedHashMap<>();
                        vendor.put("VENDOR_ID", rs.getString("VENDOR_ID"));
                        vendor.put("VENDOR_CODE", rs.getString("VENDOR_CODE"));
                        vendor.put("VENDOR_NAME", rs.getString("VENDOR_NAME"));
                        return vendor;
                    });
            result.put("VENDORS", vendors);
            return response(map, "METAOP01", "0000", "交易成功", result);
        } catch (BusinessException e) {
            return response(map, "METAOP01", e.code, e.getMessage(), Map.of());
        } catch (DataAccessException e) {
            LOG.error("Options query failed", e);
            return response(map, "METAOP01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Options query failed", e);
            return response(map, "METAOP01", "9999", "系統錯誤", Map.of());
        }
    }

    /**
     * API-4：建立請款單
     *
     * @param map
     * @return
     */
    public Map<String, Object> createInvoice(Map<String, Object> map) {
        try {
            Map<String, Object> invoice = requiredMap(request(map), "INVOICE");
            long vendorId = requiredId(invoice, "VENDOR_ID");
            String currency = requiredString(invoice, "CURRENCY_CODE", 3);
            String taxCode = requiredString(invoice, "TAX_CODE", 10);
            String item = requiredString(invoice, "ITEM", 200);
            BigDecimal subtotal = requiredAmount(invoice, "SUBTOTAL");
            LocalDate invoiceDate = requiredDate(invoice, "INVOICE_DATE");
            LocalDate dueDate = optionalDate(invoice, "DUE_DATE");
            if (dueDate != null && dueDate.isBefore(invoiceDate)) {
                throw invalid("DUE_DATE 不可早於 INVOICE_DATE");
            }
            String remarks = optionalString(invoice, "REMARKS", 1000);

            transaction.executeWithoutResult(ignored -> {
                List<String> vendors = jdbc.query("SELECT STATUS FROM VENDOR WHERE VENDOR_ID = ? FOR UPDATE",
                        (rs, rowNum) -> rs.getString("STATUS"), vendorId);
                if (vendors.isEmpty()) throw new BusinessException("E702", "查無廠商");
                if (!"ACTIVE".equals(vendors.get(0))) throw invalid("只能選擇啟用中的廠商");
                List<String> currencies = jdbc.query("SELECT CURRENCY_CODE FROM CURRENCY WHERE CURRENCY_CODE = ?",
                        (rs, rowNum) -> rs.getString(1), currency);
                if (currencies.isEmpty() || !currency.equals(currencies.get(0))) {
                    throw invalid("CURRENCY_CODE 無效");
                }
                List<Map<String, Object>> rates = jdbc.query("SELECT TAX_CODE, PERCENT FROM TAX_RATE WHERE TAX_CODE = ?",
                        (rs, rowNum) -> {
                            Map<String, Object> rate = new LinkedHashMap<>();
                            rate.put("CODE", rs.getString("TAX_CODE"));
                            rate.put("PERCENT", rs.getBigDecimal("PERCENT"));
                            return rate;
                        }, taxCode);
                if (rates.isEmpty() || !taxCode.equals(rates.get(0).get("CODE"))) {
                    throw invalid("TAX_CODE 無效");
                }
                BigDecimal percent = (BigDecimal) rates.get(0).get("PERCENT");
                BigDecimal total = subtotal.multiply(BigDecimal.ONE.add(percent.movePointLeft(2)))
                        .setScale(2, RoundingMode.HALF_UP);
                if (total.compareTo(MAX_AMOUNT) > 0) throw invalid("含稅金額超過上限");
                String invoiceNo = nextInvoiceNumber(invoiceDate);
                jdbc.update("INSERT INTO INVOICE (INVOICE_NO, VENDOR_ID, CURRENCY_CODE, TAX_CODE, TAX_PERCENT, "
                                + "ITEM, SUBTOTAL, TOTAL, INVOICE_DATE, DUE_DATE, REMARKS, STATUS) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')",
                        invoiceNo, vendorId, currency, taxCode, percent, item, subtotal, total,
                        java.sql.Date.valueOf(invoiceDate), dueDate == null ? null : java.sql.Date.valueOf(dueDate), remarks);
            });
            return response(map, "INVCRT01", "0000", "交易成功", Map.of());
        } catch (BusinessException e) {
            return response(map, "INVCRT01", e.code, e.getMessage(), Map.of());
        } catch (DataAccessException e) {
            LOG.error("Invoice creation failed", e);
            return response(map, "INVCRT01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Invoice creation failed", e);
            return response(map, "INVCRT01", "9999", "系統錯誤", Map.of());
        }
    }

    private String nextInvoiceNumber(LocalDate invoiceDate) {
        String period = invoiceDate.format(INVOICE_PERIOD);
        jdbc.update("INSERT IGNORE INTO INVOICE_NUMBER_COUNTER (PERIOD, LAST_NUMBER) VALUES (?, 0)", period);
        Long lastNumber = jdbc.queryForObject(
                "SELECT LAST_NUMBER FROM INVOICE_NUMBER_COUNTER WHERE PERIOD = ? FOR UPDATE", Long.class, period);
        Long largestNumber = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(INVOICE_NO, 12) AS UNSIGNED)), 0) "
                        + "FROM INVOICE WHERE INVOICE_NO REGEXP ?", Long.class, "^INV-" + period + "-[0-9]+$");
        long next = Math.addExact(Math.max(lastNumber, largestNumber), 1);
        jdbc.update("UPDATE INVOICE_NUMBER_COUNTER SET LAST_NUMBER = ? WHERE PERIOD = ?", next, period);
        return String.format(Locale.ROOT, "INV-%s-%03d", period, next);
    }

    /**
     * API-5：請款單列表查詢
     *
     * @param map
     * @return
     */
    public Map<String, Object> queryInvoice(Map<String, Object> map) {
        try {
            String status = requiredString(request(map), "STATUS", 20);
            if (!"DRAFT".equals(status) && !"SUBMITTED".equals(status)) {
                throw invalid("STATUS 必須為 DRAFT 或 SUBMITTED");
            }
            List<Map<String, Object>> invoices = jdbc.query(
                    "SELECT i.INVOICE_ID, i.INVOICE_NO, v.VENDOR_NAME, i.ITEM, i.CURRENCY_CODE, "
                            + "i.SUBTOTAL, i.TOTAL, i.INVOICE_DATE, i.DUE_DATE, i.REMARKS "
                            + "FROM INVOICE i JOIN VENDOR v ON v.VENDOR_ID = i.VENDOR_ID "
                            + "WHERE i.STATUS = ? ORDER BY i.INVOICE_ID",
                    (rs, rowNum) -> {
                        Map<String, Object> invoice = new LinkedHashMap<>();
                        invoice.put("INVOICE_ID", rs.getLong("INVOICE_ID"));
                        invoice.put("INVOICE_NO", rs.getString("INVOICE_NO"));
                        invoice.put("VENDOR_NAME", rs.getString("VENDOR_NAME"));
                        invoice.put("ITEM", rs.getString("ITEM"));
                        invoice.put("CURRENCY_CODE", rs.getString("CURRENCY_CODE"));
                        invoice.put("SUBTOTAL", rs.getBigDecimal("SUBTOTAL"));
                        invoice.put("TOTAL", rs.getBigDecimal("TOTAL"));
                        invoice.put("INVOICE_DATE", rs.getDate("INVOICE_DATE").toLocalDate().toString());
                        java.sql.Date due = rs.getDate("DUE_DATE");
                        invoice.put("DUE_DATE", due == null ? null : due.toLocalDate().toString());
                        invoice.put("REMARKS", rs.getString("REMARKS"));
                        return invoice;
                    }, status);
            return response(map, "INVQRY01", "0000", "交易成功", Map.of("INVOICE_LIST", invoices));
        } catch (BusinessException e) {
            return response(map, "INVQRY01", e.code, e.getMessage(), Map.of());
        } catch (DataAccessException e) {
            LOG.error("Invoice query failed", e);
            return response(map, "INVQRY01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Invoice query failed", e);
            return response(map, "INVQRY01", "9999", "系統錯誤", Map.of());
        }
    }

    /**
     * API-6：請款單狀態異動（刪除 / 送審 / 核准 / 退回）
     *
     * @param map
     * @return
     */
    public Map<String, Object> updateInvoiceStatus(Map<String, Object> map) {
        try {
            Map<String, Object> request = request(map);
            long invoiceId = requiredId(request, "INVOICE_ID");
            String action = requiredString(request, "ACTION", 10);
            if (!"SUBMIT".equals(action) && !"DELETE".equals(action) && !"REJECT".equals(action)) {
                throw invalid("ACTION 必須為 SUBMIT、DELETE 或 REJECT");
            }
            String expectedStatus = "REJECT".equals(action) ? "SUBMITTED" : "DRAFT";
            String newStatus = switch (action) {
                case "SUBMIT" -> "SUBMITTED";
                case "DELETE" -> "DELETED";
                default -> "REJECTED";
            };
            Map<String, Object> result = transaction.execute(ignored -> {
                List<String> statuses = jdbc.query("SELECT STATUS FROM INVOICE WHERE INVOICE_ID = ? FOR UPDATE",
                        (rs, rowNum) -> rs.getString("STATUS"), invoiceId);
                if (statuses.isEmpty()) throw new BusinessException("E702", "查無請款單");
                String oldStatus = statuses.get(0);
                if (!expectedStatus.equals(oldStatus)) {
                    throw invalid("REJECT".equals(action) ? "只有送審請款單可以退回" : "只有草稿請款單可以送審或刪除");
                }
                jdbc.update("UPDATE INVOICE SET STATUS = ? WHERE INVOICE_ID = ?", newStatus, invoiceId);
                Map<String, Object> changed = new LinkedHashMap<>();
                changed.put("INVOICE_ID", invoiceId);
                changed.put("OLD_STATUS", oldStatus);
                changed.put("NEW_STATUS", newStatus);
                return changed;
            });
            return response(map, "INVUPD01", "0000", "交易成功", result);
        } catch (BusinessException e) {
            return response(map, "INVUPD01", e.code, e.getMessage(), Map.of());
        } catch (DataAccessException e) {
            LOG.error("Invoice status update failed", e);
            return response(map, "INVUPD01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Invoice status update failed", e);
            return response(map, "INVUPD01", "9999", "系統錯誤", Map.of());
        }
    }

    /**
     * API-7：付款登錄
     *
     * @param map
     * @return
     */
    public Map<String, Object> upsertPayment(Map<String, Object> map) {
        try {
            Map<String, Object> payment = requiredMap(request(map), "PAYMENT");
            long invoiceId = requiredId(payment, "INVOICE_ID");
            String invoiceNo = requiredString(payment, "INVOICE_NO", 40);
            BigDecimal paidTotal = requiredAmount(payment, "PAID_TOTAL");
            LocalDate paymentDate = requiredDate(payment, "PAYMENT_DATE");
            String payerRemark = optionalString(payment, "PAYER_REMARK", 1000);

            transaction.executeWithoutResult(ignored -> {
                List<Map<String, Object>> invoices = jdbc.query(
                        "SELECT INVOICE_NO, STATUS, TOTAL FROM INVOICE WHERE INVOICE_ID = ? FOR UPDATE",
                        (rs, rowNum) -> {
                            Map<String, Object> invoice = new LinkedHashMap<>();
                            invoice.put("INVOICE_NO", rs.getString("INVOICE_NO"));
                            invoice.put("STATUS", rs.getString("STATUS"));
                            invoice.put("TOTAL", rs.getBigDecimal("TOTAL"));
                            return invoice;
                        }, invoiceId);
                if (invoices.isEmpty()) throw new BusinessException("E702", "查無請款單");
                Map<String, Object> invoice = invoices.get(0);
                if (!invoiceNo.equals(invoice.get("INVOICE_NO"))) throw invalid("INVOICE_NO 與請款單不符");
                if (!"SUBMITTED".equals(invoice.get("STATUS"))) throw invalid("只有送審請款單可以付款");
                if (paidTotal.compareTo((BigDecimal) invoice.get("TOTAL")) != 0) {
                    throw invalid("PAID_TOTAL 必須等於請款單含稅金額");
                }
                jdbc.update("INSERT INTO PAYMENT (INVOICE_ID, INVOICE_NO, PAID_TOTAL, PAYMENT_DATE, PAYER_REMARK) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        invoiceId, invoiceNo, paidTotal, java.sql.Date.valueOf(paymentDate), payerRemark);
                int updated = jdbc.update("UPDATE INVOICE SET STATUS = 'PAID' WHERE INVOICE_ID = ? AND STATUS = 'SUBMITTED'",
                        invoiceId);
                if (updated != 1) throw new IllegalStateException("Invoice payment status update failed");
            });
            return response(map, "PAYUPS01", "0000", "交易成功", Map.of());
        } catch (BusinessException e) {
            return response(map, "PAYUPS01", e.code, e.getMessage(), Map.of());
        } catch (DataAccessException e) {
            LOG.error("Payment registration failed", e);
            return response(map, "PAYUPS01", "9999", "系統錯誤", Map.of());
        } catch (RuntimeException e) {
            LOG.error("Payment registration failed", e);
            return response(map, "PAYUPS01", "9999", "系統錯誤", Map.of());
        }
    }

    /**
     * API-8：匯出 Excel
     *
     * @param map
     * @return
     */
    public Map<String, Object> exportInvoice(Map<String, Object> map) {
        // 邏輯處理
        return null;
    }

    private void saveSingleBank(long vendorId, Map<String, Object> input, boolean isNewVendor) {
        if (isNewVendor) {
            insertBank(vendorId, parseBank(input, null, true));
            return;
        }
        List<BankAccount> existing = loadBanks(vendorId);
        BankAccount primary = existing.stream().filter(bank -> bank.isDefault).findFirst()
                .orElseThrow(() -> invalid("廠商缺少預設銀行帳戶"));
        BankAccount updated = parseBank(input, primary, true);
        updateBank(updated);
    }

    private void saveBankAccounts(long vendorId, List<Map<String, Object>> inputs, boolean isNewVendor) {
        if (inputs.isEmpty() && isNewVendor) throw invalid("至少需要一筆銀行帳戶");
        List<BankAccount> existing = isNewVendor ? List.of() : loadBanks(vendorId);
        Map<Long, BankAccount> all = new LinkedHashMap<>();
        for (BankAccount bank : existing) all.put(bank.id, bank);
        List<BankAccount> changed = new ArrayList<>();
        Long requestedDefaultId = null;
        BankAccount requestedNewDefault = null;
        Map<Long, Boolean> seen = new HashMap<>();
        for (Map<String, Object> input : inputs) {
            Long accountId = optionalId(input, "ACCOUNT_ID");
            if (accountId != null && seen.putIfAbsent(accountId, true) != null) {
                throw invalid("ACCOUNT_ID 不可重複");
            }
            BankAccount old = accountId == null ? null : all.get(accountId);
            if (accountId != null && old == null) throw invalid("ACCOUNT_ID 不屬於此廠商");
            BankAccount bank = parseBank(input, old, false);
            Boolean requested = optionalBoolean(input, "IS_DEFAULT");
            if (requested != null) bank.isDefault = requested;
            if (Boolean.TRUE.equals(requested)) {
                if (requestedDefaultId != null || requestedNewDefault != null) {
                    throw invalid("只能指定一筆預設帳戶");
                }
                if (accountId == null) requestedNewDefault = bank;
                else requestedDefaultId = accountId;
            }
            changed.add(bank);
            if (accountId != null) all.put(accountId, bank);
        }
        if (isNewVendor && changed.size() == 1 && requestedNewDefault == null) {
            changed.get(0).isDefault = true;
        }
        if (requestedDefaultId != null || requestedNewDefault != null) {
            for (BankAccount bank : all.values()) bank.isDefault = false;
            for (BankAccount bank : changed) bank.isDefault = false;
            if (requestedDefaultId != null) all.get(requestedDefaultId).isDefault = true;
            else requestedNewDefault.isDefault = true;
        }
        int totalAccounts = all.size();
        int defaultCount = 0;
        for (BankAccount bank : all.values()) if (bank.isDefault) defaultCount++;
        for (BankAccount bank : changed) {
            if (bank.id == null) {
                totalAccounts++;
                if (bank.isDefault) defaultCount++;
            }
        }
        if (totalAccounts == 0 || defaultCount != 1) {
            throw invalid("銀行帳戶至少一筆，且恰好一筆為預設");
        }

        // The vendor row is locked by upsertVendor, so two changes to the default
        // account for the same vendor cannot interleave.
        if (!isNewVendor && (requestedDefaultId != null || requestedNewDefault != null)) {
            jdbc.update("UPDATE VENDOR_BANK_ACCOUNT SET IS_DEFAULT = FALSE WHERE VENDOR_ID = ?", vendorId);
        }
        for (BankAccount bank : changed) {
            if (bank.id == null) insertBank(vendorId, bank);
            else updateBank(bank);
        }
    }

    private List<BankAccount> loadBanks(long vendorId) {
        return jdbc.query("SELECT ACCOUNT_ID, BANK_CODE, BANK_NAME, ACCOUNT_NAME, ACCOUNT_NUMBER, "
                        + "ACCOUNT_CCY, IS_DEFAULT FROM VENDOR_BANK_ACCOUNT WHERE VENDOR_ID = ? "
                        + "ORDER BY ACCOUNT_ID FOR UPDATE",
                (rs, rowNum) -> new BankAccount(rs.getLong("ACCOUNT_ID"), rs.getString("BANK_CODE"),
                        rs.getString("BANK_NAME"), rs.getString("ACCOUNT_NAME"),
                        rs.getString("ACCOUNT_NUMBER"), rs.getString("ACCOUNT_CCY"),
                        rs.getBoolean("IS_DEFAULT")), vendorId);
    }

    private BankAccount parseBank(Map<String, Object> input, BankAccount old, boolean defaultAccount) {
        String bankCode = requiredString(input, "BANK_CODE", 10);
        String bankName = requiredString(input, "BANK_NAME", 100);
        String accountName = requiredString(input, "ACCOUNT_NAME", 200);
        String number = optionalString(input, "ACCOUNT_NUMBER", 50);
        if (number != null && number.contains("*")) throw invalid("不可提交遮罩後的銀行帳號");
        if (number == null && old != null) number = old.accountNumber;
        if (number == null) throw invalid("ACCOUNT_NUMBER 為必填");
        String ccy = requiredString(input, "ACCOUNT_CCY", 3);
        if (!ccy.matches("[A-Z]{3}")) throw invalid("ACCOUNT_CCY 必須為三位大寫字母");
        return new BankAccount(old == null ? null : old.id, bankCode, bankName, accountName,
                number, ccy, old == null ? defaultAccount : old.isDefault);
    }

    private void insertBank(long vendorId, BankAccount bank) {
        jdbc.update("INSERT INTO VENDOR_BANK_ACCOUNT (VENDOR_ID, BANK_CODE, BANK_NAME, ACCOUNT_NAME, "
                        + "ACCOUNT_NUMBER, ACCOUNT_CCY, IS_DEFAULT) VALUES (?, ?, ?, ?, ?, ?, ?)",
                vendorId, bank.bankCode, bank.bankName, bank.accountName,
                bank.accountNumber, bank.accountCcy, bank.isDefault);
    }

    private void updateBank(BankAccount bank) {
        jdbc.update("UPDATE VENDOR_BANK_ACCOUNT SET BANK_CODE = ?, BANK_NAME = ?, ACCOUNT_NAME = ?, "
                        + "ACCOUNT_NUMBER = ?, ACCOUNT_CCY = ?, IS_DEFAULT = ? WHERE ACCOUNT_ID = ?",
                bank.bankCode, bank.bankName, bank.accountName, bank.accountNumber,
                bank.accountCcy, bank.isDefault, bank.id);
    }

    private static Map<String, Object> bankResponse(long id, String bankCode, String bankName,
                                                     String accountName, String accountNumber,
                                                     String accountCcy, boolean isDefault) {
        Map<String, Object> bank = new LinkedHashMap<>();
        bank.put("ACCOUNT_ID", id);
        bank.put("BANK_CODE", bankCode);
        bank.put("BANK_NAME", bankName);
        bank.put("ACCOUNT_NAME", accountName);
        bank.put("ACCOUNT_NUMBER_MASKED", "****" + accountNumber.substring(Math.max(0, accountNumber.length() - 4)));
        bank.put("ACCOUNT_CCY", accountCcy);
        bank.put("IS_DEFAULT", isDefault);
        return bank;
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static Map<String, Object> request(Map<String, Object> map) {
        if (map == null || !(map.get("MWHEADER") instanceof Map<?, ?> header)
                || !(header.get("MSGID") instanceof String msgId) || msgId.isBlank()) {
            throw invalid("MWHEADER.MSGID 為必填");
        }
        return requiredMap(map, "TRANRQ");
    }

    private static Map<String, Object> requiredMap(Map<String, Object> parent, String key) {
        if (!(parent.get(key) instanceof Map<?, ?> raw)) throw invalid(key + " 格式錯誤");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String name)) throw invalid(key + " 格式錯誤");
            result.put(name, entry.getValue());
        }
        return result;
    }

    private static List<Map<String, Object>> requiredList(Map<String, Object> parent, String key) {
        if (!(parent.get(key) instanceof List<?> raw)) throw invalid(key + " 格式錯誤");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?>)) throw invalid(key + " 格式錯誤");
            result.add(requiredMap(Map.of("ITEM", item), "ITEM"));
        }
        return result;
    }

    private static String optionalString(Map<String, Object> parent, String key, int maxLength) {
        Object raw = parent.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String value)) throw invalid(key + " 格式錯誤");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > maxLength) throw invalid(key + " 長度超過 " + maxLength);
        return trimmed;
    }

    private static String requiredString(Map<String, Object> parent, String key, int maxLength) {
        String value = optionalString(parent, key, maxLength);
        if (value == null) throw invalid(key + " 為必填");
        return value;
    }

    private static long requiredId(Map<String, Object> parent, String key) {
        Long value = optionalId(parent, key);
        if (value == null) throw invalid(key + " 為必填");
        return value;
    }

    private static BigDecimal requiredAmount(Map<String, Object> parent, String key) {
        Object raw = parent.get(key);
        if (!(raw instanceof Number)) throw invalid(key + " 必須為數字");
        try {
            BigDecimal value = new BigDecimal(raw.toString());
            if (value.signum() < 0 || value.compareTo(MAX_AMOUNT) > 0) throw invalid(key + " 金額超出範圍");
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException e) {
            throw invalid(key + " 金額格式錯誤，最多兩位小數");
        }
    }

    private static LocalDate requiredDate(Map<String, Object> parent, String key) {
        LocalDate value = optionalDate(parent, key);
        if (value == null) throw invalid(key + " 為必填");
        return value;
    }

    private static LocalDate optionalDate(Map<String, Object> parent, String key) {
        String text = optionalString(parent, key, 10);
        if (text == null) return null;
        try {
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid(key + " 必須為 YYYY-MM-DD");
            LocalDate value = LocalDate.parse(text);
            if (value.getYear() < 1000) throw invalid(key + " 年份超出範圍");
            return value;
        } catch (DateTimeParseException e) {
            throw invalid(key + " 日期無效");
        }
    }

    private static Long optionalId(Map<String, Object> parent, String key) {
        Object raw = parent.get(key);
        if (raw == null || (raw instanceof String value && value.isBlank())) return null;
        String text = raw.toString();
        if (!text.matches("[1-9][0-9]*")) throw invalid(key + " 格式錯誤");
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            throw invalid(key + " 格式錯誤");
        }
    }

    private static Boolean optionalBoolean(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) return null;
        if (value instanceof Boolean answer) return answer;
        throw invalid(key + " 格式錯誤");
    }

    private static Map<String, Object> response(Map<String, Object> request, String fallbackMsgId,
                                                 String code, String description, Map<String, Object> result) {
        String msgId = fallbackMsgId;
        if (request != null && request.get("MWHEADER") instanceof Map<?, ?> header
                && header.get("MSGID") instanceof String value && !value.isBlank()) msgId = value;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("MSGID", msgId);
        header.put("RtnCode", code);
        header.put("RtnDesc", description);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("MWHEADER", header);
        response.put("TRANRS", result);
        return response;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("E001", message);
    }

    private static final class BusinessException extends RuntimeException {
        private final String code;

        private BusinessException(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static final class BankAccount {
        private final Long id;
        private final String bankCode;
        private final String bankName;
        private final String accountName;
        private final String accountNumber;
        private final String accountCcy;
        private boolean isDefault;

        private BankAccount(Long id, String bankCode, String bankName, String accountName,
                            String accountNumber, String accountCcy, boolean isDefault) {
            this.id = id;
            this.bankCode = bankCode;
            this.bankName = bankName;
            this.accountName = accountName;
            this.accountNumber = accountNumber;
            this.accountCcy = accountCcy;
            this.isDefault = isDefault;
        }
    }
}
