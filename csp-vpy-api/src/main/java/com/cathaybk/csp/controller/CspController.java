package com.cathaybk.csp.controller;

import com.cathaybk.csp.service.CspService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin({ "*" })
@RequestMapping("/csp/api")
public class CspController {

    /** CspService */
    @Autowired
    private CspService cspService;

    /**
     * 範例程式
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/demoCode")
    public ResponseEntity<Map<String, Object>> demoCode(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.demoCode(map), HttpStatus.OK);
    }

    /**
     * API-1：廠商查詢（模糊條件）
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/vendor/query")
    public ResponseEntity<Map<String, Object>> queryVendor(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.queryVendor(map), HttpStatus.OK);
    }

    /**
     * API-2：廠商新增 / 更新
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/vendor/upsert")
    public ResponseEntity<Map<String, Object>> upsertVendor(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.upsertVendor(map), HttpStatus.OK);
    }

    /**
     * API-3：取得下拉選單（廠商 / 稅率 / 幣別）
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/meta/options")
    public ResponseEntity<Map<String, Object>> queryOptions(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.queryOptions(map), HttpStatus.OK);
    }

    /**
     * API-4：建立請款單
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/invoice/create")
    public ResponseEntity<Map<String, Object>> createInvoice(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.createInvoice(map), HttpStatus.OK);
    }

    /**
     * API-5：請款單列表查詢
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/invoice/query")
    public ResponseEntity<Map<String, Object>> queryInvoice(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.queryInvoice(map), HttpStatus.OK);
    }

    /**
     * API-6：請款單狀態異動（刪除 / 送審 / 核准 / 退回）
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/invoice/updateStatus")
    public ResponseEntity<Map<String, Object>> updateInvoiceStatus(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.updateInvoiceStatus(map), HttpStatus.OK);
    }

    /**
     * API-7：付款登錄
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/payment/upsert")
    public ResponseEntity<Map<String, Object>> upsertPayment(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.upsertPayment(map), HttpStatus.OK);
    }

    /**
     * API-8：匯出 Excel
     *
     * @param map
     * @return
     */
    @ResponseBody
    @PostMapping(value = "/invoice/export")
    public ResponseEntity<Map<String, Object>> exportInvoice(@RequestBody Map<String, Object> map) {
        return new ResponseEntity<>(cspService.exportInvoice(map), HttpStatus.OK);
    }

}