// app.js — 前端呼叫 API 與最小 UI 行為

/***************
 *  API 呼叫層
 ***************/
const API_BASE = "http://localhost:8080/csp/api";

// API 層級錯誤
function showAlert(title, subtitle) {
  Swal.fire({
    title: title,
    text: subtitle,
    icon: 'warning', // 你可以選擇不同的圖示，例如 'success', 'error', 'warning', 'info', 'question'
    confirmButtonText: '確定'
  });
}

async function post(path, msgid, tranrq = {}) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ MWHEADER: { MSGID: msgid }, TRANRQ: tranrq })
  });

  if (!res.ok) {
    // HTTP 層級錯誤
    showAlert("系統異常", `HTTP ${res.status}`);
    throw new Error("HTTP " + res.status);
  }

  const json = await res.json();

  if (json?.MWHEADER?.RtnCode !== "0000") {
    const desc = json?.MWHEADER?.RtnDesc || "API Error";
    showAlert("交易失敗", desc);
    throw new Error(desc);
  }

  return json.TRANRS;
}

const API = {
  // 統一：所有 API 都用 post(path, msgid, tranrq)
  post,
  vendorQuery: (q = {}) => {
    return post("/vendor/query", "VDRQRY01", {
      TAX_ID: q.TAX_ID ?? undefined,
      VENDOR_NAME: q.VENDOR_NAME ?? undefined,
      STATUS: q.STATUS ?? undefined
    });
  },

  vendorUpsert: (v) => {
    return post("/vendor/upsert", "VDRUPS01", { VENDOR: v });
  },

  metaOptions: () => {
    // 請求所有必要的選項資料，包含廠商清單
    return post("/meta/options", "METAOP01", { NEED: ["CURRENCIES", "TAX_RATES", "VENDORS"] });
  },

  invCreate: (invoice) => {
    return post("/invoice/create", "INVCRT01", { INVOICE: invoice });
  },

  invoiceQuery: (status) => {
    return post("/invoice/query", "INVQRY01", { STATUS: status });
  },

  invoiceUpdateStatus: (invoiceId, action) => {
    return post("/invoice/updateStatus", "INVUPD01", {
      INVOICE_ID: invoiceId,
      ACTION: action
    });
  },

  paymentUpsert: (payment) => {
    return post("/payment/upsert", "PAYUPS01", { PAYMENT: payment });
  },

  invoiceExport: () => {
    return post("/invoice/export", "INVEPT01", {});
  }
};

/***************
 *  簡易 UI 工具
 ***************/
const $ = (sel) => document.querySelector(sel);
const knownInvoices = new Map(); // 以 INVOICE_ID 作 key，供審核頁顯示

/***************
 *  分頁切換
 ***************/
document.querySelectorAll(".tab").forEach(t => {
  t.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach(x => x.classList.remove("active"));
    t.classList.add("active");
    const tab = t.dataset.tab;
    $("#page-vendor").classList.toggle("hidden", tab !== "vendor");
    $("#page-invoice").classList.toggle("hidden", tab !== "invoice");
    $("#page-approval").classList.toggle("hidden", tab !== "approval");
    if (tab === "approval") {
      searchApprovalList();
    }
  });
});

/***************
 *  畫面一：廠商維護
 ***************/
const renderVendors = (items = []) => {
  const tbody = $("#vendor-tbody");
  if (!tbody) return;
  tbody.innerHTML = "";
  items.forEach(v => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${v.VENDOR_CODE}</td>
      <td>${v.TAX_ID}</td>
      <td>${v.VENDOR_NAME}</td>
      <td>${v.BANK ? (v.BANK.BANK_CODE + " " + v.BANK.BANK_NAME) : "-"}</td>
      <td>${v.BANK ? v.BANK.ACCOUNT_NAME : "-"}</td>
      <td>${v.BANK ? v.BANK.ACCOUNT_NUMBER_MASKED : "-"}</td>
      <td>${v.ADDRESS || ""}</td>
      <td>${v.PHONE || ""}</td>
      <td>${v.EMAIL || ""}</td>
      <td><button class="btn ghost btn-sm inline" data-id="${v.VENDOR_ID}">編輯</button></td>
    `;
    tbody.appendChild(tr);
  });
  tbody.querySelectorAll("button[data-id]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const id = Number(btn.dataset.id);
      const v = items.find(x => x.VENDOR_ID === id);
      $("#vendor-edit").classList.remove("hidden");
      $("#ve-code").value = v.VENDOR_CODE || "";
      $("#ve-tax").value = v.TAX_ID || "";
      $("#ve-name").value = v.VENDOR_NAME || "";
      $("#ve-status").value = v.STATUS || "ACTIVE";
      $("#ve-email").value = v.EMAIL || "";
      $("#ve-phone").value = v.PHONE || "";
      $("#ve-addr").value = v.ADDRESS || "";
      $("#ve-bank-code").value = v.BANK?.BANK_CODE || "";
      $("#ve-bank-name").value = v.BANK?.BANK_NAME || "";
      $("#ve-bank-acname").value = v.BANK?.ACCOUNT_NAME || "";
      $("#ve-bank-acno").value = v.BANK?.ACCOUNT_NUMBER || "";
      $("#ve-bank-ccy").value = v.BANK?.ACCOUNT_CCY || "";
      $("#btn-ve-save").onclick = async () => {
        const vendor = {
          VENDOR_ID: v.VENDOR_ID,
          VENDOR_CODE: $("#ve-code").value,
          TAX_ID: $("#ve-tax").value,
          VENDOR_NAME: $("#ve-name").value,
          STATUS: $("#ve-status").value,
          EMAIL: $("#ve-email").value,
          PHONE: $("#ve-phone").value,
          ADDRESS: $("#ve-addr").value,
          BANK: {
            BANK_CODE: $("#ve-bank-code").value,
            BANK_NAME: $("#ve-bank-name").value,
            ACCOUNT_NAME: $("#ve-bank-acname").value,
            ACCOUNT_NUMBER: $("#ve-bank-acno").value,
            ACCOUNT_CCY: $("#ve-bank-ccy").value
          }
        };
        await API.vendorUpsert(vendor);
        alert("已儲存");
        $("#vendor-edit").classList.add("hidden");
        $("#btn-vendor-search").click();
      };
      $("#btn-ve-cancel").onclick = () => $("#vendor-edit").classList.add("hidden");
    });
  });
};

$("#btn-vendor-search")?.addEventListener("click", async () => {
  const rs = await API.vendorQuery({
    TAX_ID: $("#q-taxid").value || undefined,
    VENDOR_NAME: $("#q-name").value || undefined,
    STATUS: $("#q-status").value || undefined
  });
  renderVendors(rs.VENDORS || []);
});

$("#btn-vendor-clear")?.addEventListener("click", () => {
  $("#q-taxid").value = ""; $("#q-name").value = ""; $("#q-status").value = ""; $("#q-page").value = 1; $("#q-size").value = 20;
});

$("#btn-vendor-new")?.addEventListener("click", () => {
  $("#vendor-edit").classList.remove("hidden");
  $("#ve-code").value = ""; $("#ve-tax").value = ""; $("#ve-name").value = ""; $("#ve-status").value = "ACTIVE";
  $("#ve-email").value = ""; $("#ve-phone").value = ""; $("#ve-addr").value = "";
  $("#ve-bank-code").value = ""; $("#ve-bank-name").value = ""; $("#ve-bank-acname").value = ""; $("#ve-bank-acno").value = ""; $("#ve-bank-ccy").value = "";
  $("#btn-ve-save").onclick = async () => {
    const vendor = {
      VENDOR_ID: null,
      VENDOR_CODE: $("#ve-code").value || undefined,
      TAX_ID: $("#ve-tax").value,
      VENDOR_NAME: $("#ve-name").value,
      STATUS: $("#ve-status").value,
      EMAIL: $("#ve-email").value,
      PHONE: $("#ve-phone").value,
      ADDRESS: $("#ve-addr").value,
      BANK: {
        BANK_CODE: $("#ve-bank-code").value,
        BANK_NAME: $("#ve-bank-name").value,
        ACCOUNT_NAME: $("#ve-bank-acname").value,
        ACCOUNT_NUMBER: $("#ve-bank-acno").value,
        ACCOUNT_CCY: $("#ve-bank-ccy").value
      }
    };
    await API.vendorUpsert(vendor);
    alert("新增成功");
    $("#vendor-edit").classList.add("hidden");
    $("#btn-vendor-search").click();
  };
  $("#btn-ve-cancel").onclick = () => $("#vendor-edit").classList.add("hidden");
});

/***************
 *  畫面二：建立請款單
 ***************/
async function loadMeta() {
  try {
    const rs = await API.metaOptions();

    // 載入幣別選項
    if ($("#inv-currency")) {
      $("#inv-currency").innerHTML = `<option value="">請選擇幣別</option>` +
        (rs.CURRENCIES || []).map(c => `<option value="${c.CODE}">${c.CODE}</option>`).join("");
    }
    if ($("#pay-currency")) {
      $("#pay-currency").innerHTML = `<option value="">請選擇幣別</option>` +
        (rs.CURRENCIES || []).map(c => `<option value="${c.CODE}">${c.CODE}</option>`).join("");
    }

    // 載入稅率選項
    if ($("#inv-tax")) {
      $("#inv-tax").innerHTML = `<option value="">請選擇稅率</option>` +
        (rs.TAX_RATES || []).map(t => `<option value="${t.CODE}">${t.CODE} (${t.PERCENT}%)</option>`).join("");
    }

    // 載入廠商選項
    if (rs.VENDORS && rs.VENDORS.length > 0) {
      const vendorOpts = rs.VENDORS.map(v => `<option value="${v.VENDOR_ID}">${v.VENDOR_NAME}</option>`).join("");
      if ($("#inv-vendor")) $("#inv-vendor").innerHTML = `<option value="">請選擇廠商</option>` + vendorOpts;
      if ($("#ap-vendor")) $("#ap-vendor").innerHTML = `<option value="">全部</option>` + vendorOpts;
    }
  } catch (error) {
    console.error('Failed to load meta options:', error);
    showAlert("載入失敗", "無法載入選項資料，請檢查 API 連線");
  }
}
async function loadVendorsToSelect() {
  try {
    const rs = await API.vendorQuery({ PAGE_NO: 1, PAGE_SIZE: 999 });
    console.log('Vendors loaded:', rs); // 除錯用
    const opts = (rs.VENDORS || []).map(v => `<option value="${v.VENDOR_ID}">${v.VENDOR_NAME}</option>`).join("");
    if ($("#inv-vendor")) $("#inv-vendor").innerHTML = `<option value="">請選擇廠商</option>` + opts;
    if ($("#ap-vendor")) $("#ap-vendor").innerHTML = `<option value="">全部</option>` + opts;
  } catch (error) {
    console.error('Failed to load vendors:', error);
    showAlert("載入失敗", "無法載入廠商資料，請檢查 API 連線");
  }
}

// 確保 DOM 載入完成後才初始化
document.addEventListener('DOMContentLoaded', function () {
  loadMeta(); // 只需要載入 meta 選項，廠商選項已包含在內

  // 預設載入廠商查詢
  $("#btn-vendor-search")?.click();
});
let draftLines = [];

function rerenderLines() {
  const tbody = $("#inv-lines");
  if (!tbody) return;
  tbody.innerHTML = "";
  draftLines.forEach((l, idx) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${l.LINE_NO}</td>
      <td><input value="${l.ITEM_DESC || ""}" data-k="ITEM_DESC" data-i="${idx}"></td>
      <td><input type="number" step="0.0001" value="${l.QUANTITY || 0}" data-k="QUANTITY" data-i="${idx}"></td>
      <td><input type="number" step="0.0001" value="${l.UNIT_PRICE || 0}" data-k="UNIT_PRICE" data-i="${idx}"></td>
      <td>
        <select data-k="LINE_TAX_CODE" data-i="${idx}">
          ${Array.from($("#inv-tax").options).map(o => `<option ${o.value === (l.LINE_TAX_CODE || "") ? "selected" : ""}>${o.value}</option>`).join("")}
        </select>
      </td>
      <td><input value="${l.COST_CENTER || ""}" data-k="COST_CENTER" data-i="${idx}"></td>
      <td><input value="${l.PROJECT_CODE || ""}" data-k="PROJECT_CODE" data-i="${idx}"></td>
      <td class="sum">${(l.AMOUNT_EXCL_TAX || 0).toFixed ? l.AMOUNT_EXCL_TAX.toFixed(2) : (l.AMOUNT_EXCL_TAX || 0)}</td>
      <td class="sum">${(l.TAX_AMOUNT || 0).toFixed ? l.TAX_AMOUNT.toFixed(2) : (l.TAX_AMOUNT || 0)}</td>
      <td class="sum">${(l.AMOUNT_INCL_TAX || 0).toFixed ? l.AMOUNT_INCL_TAX.toFixed(2) : (l.AMOUNT_INCL_TAX || 0)}</td>
      <td><button class="btn danger ghost btn-sm" data-del="${idx}">刪</button></td>
    `;
    tbody.appendChild(tr);
  });
  tbody.querySelectorAll("input,select").forEach(el => {
    el.addEventListener("change", () => {
      const i = Number(el.dataset.i), k = el.dataset.k;
      draftLines[i][k] = (el.type === "number") ? Number(el.value) : el.value;
    });
  });
  tbody.querySelectorAll("button[data-del]").forEach(btn => {
    btn.addEventListener("click", () => {
      const i = Number(btn.dataset.del);
      draftLines.splice(i, 1);
      draftLines.forEach((l, idx) => l.LINE_NO = idx + 1);
      rerenderLines();
    });
  });
}

$("#btn-inv-create")?.addEventListener("click", async () => {
  const invoice = {
    VENDOR_ID: Number($("#inv-vendor").value),
    CURRENCY_CODE: $("#inv-currency").value,
    TAX_CODE: $("#inv-tax").value,
    INVOICE_DATE: $("#inv-date").value || new Date().toISOString().slice(0, 10),
    DUE_DATE: $("#inv-due").value || null,
    ITEM: $("#inv-item").value || "",
    SUBTOTAL: Number($("#inv-subtotal").value) || 0,
    REMARKS: $("#inv-remark").value
  };
  await API.invCreate(invoice);
  alert("請款單已建立");
});

// 重新整理請款單草稿列表
$("#btn-inv-refresh")?.addEventListener("click", async () => {
  const rs = await API.invoiceQuery("DRAFT");
  renderInvoiceList(rs.INVOICE_LIST || [], "#invoice-list-tbody");
});

// 請款單草稿列表渲染函數
function renderInvoiceList(list = [], tbodyId) {
  const tbody = document.querySelector(tbodyId);
  if (!tbody) return;
  tbody.innerHTML = "";
  list.forEach((inv, idx) => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      ${tbodyId === "#approval-list-tbody" ? `<td><input type="radio" name="ap-pick" class="ap-pick" data-id="${inv.INVOICE_ID}" data-no="${inv.INVOICE_NO}"></td>` : ""}
      <td>${inv.INVOICE_NO || ""}</td>
      <td>${inv.VENDOR_NAME || ""}</td>
      <td>${inv.ITEM || ""}</td>
      <td>${inv.CURRENCY_CODE || ""}</td>
      <td class="sum">${Number(inv.SUBTOTAL || 0).toFixed(2)}</td>
      <td class="sum">${Number(inv.TOTAL || 0).toFixed(2)}</td>
      <td>${inv.INVOICE_DATE || ""}</td>
      <td>${inv.DUE_DATE || ""}</td>
      <td>${inv.REMARKS || ""}</td>
      <td>
      ${tbodyId === "#invoice-list-tbody"
        ? `
          <button class="btn ghost btn-sm" data-submit="${inv.INVOICE_ID}" id="btn-inv-submit-${idx}">送審</button>
          <button class="btn danger ghost btn-sm" data-delete="${inv.INVOICE_ID}" id="btn-inv-delete-${idx}">刪除</button>
        `
        : tbodyId === "#approval-list-tbody"
          ? `
          <button class="btn danger ghost btn-sm" data-reject="${inv.INVOICE_ID}" id="btn-inv-reject-${idx}">退回</button>
        `
          : ""
      }
      </td>
    `;
    tbody.appendChild(tr);
  });
  // radio 選擇事件（只在待審核清單）
  if (tbodyId === "#approval-list-tbody") {
    tbody.querySelectorAll("input.ap-pick[type=radio]").forEach(radio => {
      radio.addEventListener("change", function () {
        if (this.checked) {
          document.getElementById("selected-invoice-id").textContent = this.dataset.no;
        }
      });
    });
  }

  // 送審按鈕事件
  tbody.querySelectorAll("button[data-submit]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const invoiceId = Number(btn.dataset.submit);
      const invoice = list.find(x => x.INVOICE_ID === invoiceId);
      if (!invoice) return alert("找不到請款單資料");
      try {
        const rs = await API.invoiceUpdateStatus(invoice.INVOICE_ID, "SUBMIT");
        alert(`送審成功：${invoice.INVOICE_NO}\n狀態：${rs.OLD_STATUS} → ${rs.NEW_STATUS}`);
        $("#btn-inv-refresh").click();
      } catch (e) {
        alert("送審失敗: " + e.message);
      }
    });
  });

  // 拒絕審核按鈕事件
  tbody.querySelectorAll("button[data-reject]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const invoiceId = Number(btn.dataset.reject);
      const invoice = list.find(x => x.INVOICE_ID === invoiceId);
      if (!invoice) return alert("找不到請款單資料");
      try {
        const rs = await API.invoiceUpdateStatus(invoice.INVOICE_ID, "REJECT");
        alert(`拒絕審核成功：${invoice.INVOICE_NO}\n狀態：${rs.OLD_STATUS} → ${rs.NEW_STATUS}`);
        $("#btn-inv-refresh").click();
      } catch (e) {
        alert("拒絕審核失敗: " + e.message);
      }
    });
  });

  // 刪除按鈕事件
  tbody.querySelectorAll("button[data-delete]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const invoiceId = Number(btn.dataset.delete);
      const invoice = list.find(x => x.INVOICE_ID === invoiceId);
      if (!invoice) return alert("找不到請款單資料");
      if (!confirm(`確定要刪除請款單 ${invoice.INVOICE_NO} 嗎？`)) return;
      try {
        const rs = await API.invoiceUpdateStatus(invoice.INVOICE_ID, "DELETE");
        alert(`刪除成功：${invoice.INVOICE_NO}\n狀態：${rs.OLD_STATUS} → ${rs.NEW_STATUS}`);
        $("#btn-inv-refresh").click();
      } catch (e) {
        alert("刪除失敗: " + e.message);
      }
    });
  });
}

/***************
 *  畫面三：審核與付款
 ***************/
let currentApprovalList = []; // 儲存當前的審核清單

async function searchApprovalList() {
  const rs = await API.invoiceQuery("SUBMITTED");
  currentApprovalList = rs.INVOICE_LIST || []; // 儲存資料供後續使用
  renderInvoiceList(currentApprovalList, "#approval-list-tbody");
}

// 重新整理待審核清單
$("#btn-pay-submit")?.addEventListener("click", async () => {
  const radio = document.querySelector(".ap-pick:checked");
  if (!radio) { alert("請先勾選請款單"); return; }
  const invoiceId = Number(radio.dataset.id);
  // 從當前審核清單中找到該請款單資料
  const invoice = currentApprovalList.find(x => x.INVOICE_ID === invoiceId);
  if (!invoice) { alert("找不到請款單資料"); return; }

  const payment = {
    INVOICE_ID: invoiceId,
    INVOICE_NO: radio.dataset.no,
    PAID_TOTAL: invoice.TOTAL,
    PAYMENT_DATE: new Date().toISOString().slice(0, 10),
    PAYER_REMARK: $("#pay-remark").value || ""
  };

  try {
    await API.paymentUpsert(payment);
    alert("付款完成");
    searchApprovalList();
  } catch (e) {
    alert("付款失敗: " + e.message);
  }
});

// 加分題：匯出 Excel
async function exportCaseList() {
  try {
    const res = await API.invoiceExport();
    // 將 Base64 編碼的字串轉為 byte array
    if (res.DATA && res.DATA.length > 0) {
      const base64Data = res.DATA[0];
      const byteCharacters = atob(base64Data);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);

      // 建立 Blob 物件
      const blob = new Blob([byteArray], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });

      // 建立下載連結
      const a = document.createElement('a');
      const objectUrl = URL.createObjectURL(blob);
      a.href = objectUrl;
      const now = new Date();
      const pad = (n) => n.toString().padStart(2, '0');
      const fileName = `請款單列表_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.xlsx`;
      a.download = fileName; // 指定檔名
      a.click(); // 觸發下載

      // 清除 URL 物件
      URL.revokeObjectURL(objectUrl);
    } else {
      showAlert("匯出失敗", "沒有收到檔案資料");
    }
  } catch (error) {
    console.error('Excel export failed:', error);
    showAlert("匯出失敗", "無法匯出 Excel 檔案，請檢查 API 連線");
  }
}

// 綁定匯出按鈕事件
$("#btn-export-excel")?.addEventListener("click", exportCaseList);