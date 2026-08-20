"use strict";

const $ = (id) => document.getElementById(id);
let chart = null;

// ---------- 工具 ----------
function parseData() {
    const text = $("data").value.trim();
    if (!text) return [];
    const out = [];
    for (const line of text.split(/\r?\n/)) {
        const cells = line.split(/[,\t;]+/).map(s => s.trim()).filter(Boolean);
        // 取最后一个数值（兼容 "index,value" 两列 CSV），忽略表头
        for (let i = cells.length - 1; i >= 0; i--) {
            const num = Number(cells[i]);
            if (!Number.isNaN(num)) { out.push(num); break; }
        }
    }
    return out;
}

function validateValues(values) {
    if (!values.length) return "请先输入数据或载入示例";
    if (values.length < 8) return "数据量过少，至少需要 8 个有效数值（当前 " + values.length + " 个）。请检查是否只粘贴了表头，或每一行最后一列不是数字。";
    return null;
}

function buildRequest(extra) {
    const req = {
        values: parseData(),
        model: $("model").value,
        horizon: Number($("horizon").value),
        m: Number($("m").value),
        seasonal: $("seasonal").value,
        p: Number($("p").value),
        d: Number($("d").value),
        q: Number($("q").value)
    };
    return Object.assign(req, extra || {});
}

// ---------- 预测 ----------
async function runForecast() {
    const req = buildRequest();
    const err = validateValues(req.values);
    if (err) { setMsg(err, true); return; }
    setMsg("预测中…");
    try {
        const resp = await post("/api/forecast", req);
        if (!resp || !Array.isArray(resp.history) || !Array.isArray(resp.forecast) ||
            !Array.isArray(resp.lower) || !Array.isArray(resp.upper)) {
            setMsg("请求失败：响应缺少必要数组字段。原始响应：" + JSON.stringify(resp).slice(0, 200), true);
            return;
        }
        $("m-model").textContent = resp.model;
        $("m-mape").textContent = fmt(resp.mape) + "%";
        $("m-rmse").textContent = fmt(resp.rmse);
        $("m-mae").textContent = fmt(resp.mae);
        $("params").textContent = JSON.stringify(resp.params, null, 2);
        renderChart(resp.history, resp.forecast, resp.lower, resp.upper);
        setMsg(resp.note || "完成");
    } catch (e) {
        setMsg("请求失败：" + e.message, true);
    }
}

// ---------- 回测 ----------
async function runBacktest() {
    const req = buildRequest({ horizon: Number($("horizon").value), step: 1 });
    const err = validateValues(req.values);
    if (err) { setMsg(err, true); return; }
    setMsg("回测中…");
    try {
        const resp = await post("/api/backtest", req);
        let html = "<table><thead><tr><th>步数</th><th>MAPE(%)</th><th>RMSE</th><th>MAE</th></tr></thead><tbody>";
        for (let i = 0; i < resp.horizonSteps.length; i++) {
            html += `<tr><td>${resp.horizonSteps[i]}</td><td>${fmt(resp.mape[i])}</td><td>${fmt(resp.rmse[i])}</td><td>${fmt(resp.mae[i])}</td></tr>`;
        }
        html += "</tbody></table>";
        html += `<div class="overall">整体：MAPE <b>${fmt(resp.overallMape)}%</b> · RMSE <b>${fmt(resp.overallRmse)}</b> · MAE <b>${fmt(resp.overallMae)}</b> · 模型 <b>${resp.model}</b></div>`;
        $("bt").innerHTML = html;
        setMsg(resp.note || "回测完成");
    } catch (e) {
        setMsg("请求失败：" + e.message, true);
    }
}

// ---------- 渲染 ----------
function renderChart(history, forecast, lower, upper) {
    const ctx = $("chart").getContext("2d");
    history = Array.isArray(history) ? history : [];
    forecast = Array.isArray(forecast) ? forecast : [];
    lower = Array.isArray(lower) ? lower : [];
    upper = Array.isArray(upper) ? upper : [];
    if (history.length === 0) {
        setMsg("无历史数据，跳过图表渲染", true);
        return;
    }
    const N = history.length, h = forecast.length, total = N + h;
    const labels = Array.from({ length: total }, (_, i) => i + 1);

    const historyData = history.concat(Array(h).fill(null));
    const forecastData = Array(total).fill(null);
    const lowerData = Array(total).fill(null);
    const upperData = Array(total).fill(null);
    forecastData[N - 1] = history[N - 1];
    lowerData[N - 1] = history[N - 1];
    upperData[N - 1] = history[N - 1];
    for (let k = 0; k < h; k++) {
        forecastData[N + k] = forecast[k];
        lowerData[N + k] = lower[k];
        upperData[N + k] = upper[k];
    }

    const ds = [
        { label: "历史", data: historyData, borderColor: "#4f8cff", backgroundColor: "#4f8cff", pointRadius: 0, borderWidth: 2, spanGaps: true },
        { label: "预测", data: forecastData, borderColor: "#ff9f43", backgroundColor: "#ff9f43", pointRadius: 2, borderWidth: 2, borderDash: [6, 4], spanGaps: true },
        { label: "下界", data: lowerData, borderColor: "transparent", pointRadius: 0, borderWidth: 0, spanGaps: true, fill: false },
        { label: "上界", data: upperData, borderColor: "transparent", pointRadius: 0, borderWidth: 0, spanGaps: true, fill: "-1", backgroundColor: "rgba(255,159,67,0.15)" }
    ];

    if (chart) chart.destroy();
    chart = new Chart(ctx, {
        type: "line",
        data: { labels, datasets: ds },
        options: {
            responsive: true, maintainAspectRatio: false,
            interaction: { mode: "index", intersect: false },
            plugins: { legend: { labels: { color: "#8a97b3" } } },
            scales: {
                x: { ticks: { color: "#8a97b3" }, grid: { color: "#1e2738" } },
                y: { ticks: { color: "#8a97b3" }, grid: { color: "#1e2738" } }
            }
        }
    });
}

// ---------- 杂项 ----------
function post(url, body) {
    return fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify(body)
    }).then(r => {
        if (!r.ok) {
            return r.json().then(err => {
                const msg = (err && (err.message || err.error)) ? (err.message || err.error) : ("HTTP " + r.status);
                throw new Error(msg);
            }).catch(() => { throw new Error("HTTP " + r.status); });
        }
        return r.json();
    });
}
function fmt(x) { return (x == null) ? "-" : (Math.round(x * 100) / 100).toFixed(2); }
function setMsg(t, err) {
    $("msg").textContent = t;
    $("msg").style.color = err ? "#ff6b6b" : "";
}

// ---------- 绑定 ----------
$("run").addEventListener("click", runForecast);
$("runbt").addEventListener("click", runBacktest);
document.querySelectorAll("[data-sample]").forEach(btn => {
    btn.addEventListener("click", async () => {
        const name = btn.getAttribute("data-sample");
        const txt = await (await fetch("sample-data/" + name)).text();
        $("data").value = txt;
        setMsg("已载入示例：" + name);
    });
});

// 健康检查
fetch("/api/health").then(r => r.text()).then(t => {
    const b = $("status-badge");
    b.textContent = "后端已连接";
    b.classList.add("ok");
}).catch(() => {
    const b = $("status-badge");
    b.textContent = "未连接后端";
    b.classList.add("err");
});
