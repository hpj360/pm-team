(function() {
  var style = getComputedStyle(document.documentElement);
  var accent = style.getPropertyValue('--accent').trim();
  var accent2 = style.getPropertyValue('--accent2').trim();
  var ink = style.getPropertyValue('--ink').trim();
  var muted = style.getPropertyValue('--muted').trim();
  var rule = style.getPropertyValue('--rule').trim();
  var bg2 = style.getPropertyValue('--bg2').trim();
  var MONO = 'JetBrainsMono, monospace';

  function prep(id, h) {
    var el = document.getElementById(id);
    if (!el) return null;
    el.style.minHeight = h + 'px';
    return echarts.init(el, null, { renderer: 'svg' });
  }
  var AXIS = {
    axisLine: { lineStyle: { color: rule } },
    axisTick: { show: false },
    axisLabel: { color: muted, fontFamily: MONO, fontSize: 11 },
    splitLine: { lineStyle: { color: rule, type: 'dashed' } }
  };

  /* ---------- 图 1 · 混合价光谱（log 横条） ---------- */
  (function() {
    var el = prep('chart-price', 580);
    if (!el) return;
    var models = [
      ['Hy4 flash', 0.12, 1], ['Haiku 5.5', 0.20, 0], ['GPT-6 Luna', 0.20, 0],
      ['GLM-5.3-Flash', 0.24, 1], ['M3.1 Flash', 0.25, 1], ['DeepSeek V4.1 Flash', 0.26, 1],
      ['MiniMax M3', 0.52, 1], ['Hy4', 0.63, 1], ['DeepSeek V4 Pro', 0.99, 1],
      ['Qwen4-Max', 2.11, 1], ['GLM-5.3', 2.15, 1], ['Gemini 4 Carbon', 4.00, 0],
      ['Kimi K3.1 Pro', 4.00, 1], ['Gemini 4 Neon', 6.00, 0],
      ['Claude Opus 5.5', 10.00, 0], ['GPT-6.2 Astra', 16.00, 0], ['Claude Fable 5', 20.00, 0]
    ];
    el.setOption({
      animation: false,
      grid: { left: 10, right: 64, top: 10, bottom: 28, containLabel: true },
      tooltip: {
        appendToBody: true,
        formatter: function(p) { return p.name + ' · 混合价 <b>$' + p.value.toFixed(2) + '</b>/M'; }
      },
      xAxis: Object.assign({}, AXIS, {
        type: 'log', logBase: 10, min: 0.09, max: 28,
        axisLabel: Object.assign({}, AXIS.axisLabel, {
          formatter: function(v) { return (v >= 1 ? '$' + v : '$' + v.toFixed(2)); }
        })
      }),
      yAxis: Object.assign({}, AXIS, { type: 'category', data: models.map(function(m) { return m[0]; }),
        axisLabel: Object.assign({}, AXIS.axisLabel, { color: ink, fontSize: 12, fontFamily: 'WorkSans, PingFang SC, sans-serif' }),
        splitLine: { show: false } }),
      series: [{
        type: 'bar', barWidth: 16,
        data: models.map(function(m) { return { value: m[1], itemStyle: { color: m[2] ? accent : accent2, opacity: 0.92 } }; }),
        label: { show: true, position: 'right', color: ink, fontFamily: MONO, fontSize: 11,
          formatter: function(p) { return '$' + p.value.toFixed(2); } }
      }]
    });
    window.addEventListener('resize', function() { el.resize(); });
  })();

  /* ---------- 图 2 · AA 独立智能指数 ---------- */
  (function() {
    var el = prep('chart-aa', 400);
    if (!el) return;
    var models = [
      ['DeepSeek V4.1 Flash', 39, 1], ['GLM-5.3-Flash', 42, 1], ['Qwen4-Max', 43, 1],
      ['GPT-6 Sol', 44, 0], ['GLM-5.3', 45, 1], ['Claude Opus 5.5', 59, 0],
      ['Gemini 4 Carbon', 60, 0], ['Gemini 4 Neon', 61, 0]
    ];
    el.setOption({
      animation: false,
      grid: { left: 10, right: 52, top: 10, bottom: 28, containLabel: true },
      tooltip: { appendToBody: true, formatter: function(p) { return p.name + ' · AA 指数 <b>' + p.value + '</b>'; } },
      xAxis: Object.assign({}, AXIS, { type: 'value', min: 0, max: 70 }),
      yAxis: Object.assign({}, AXIS, { type: 'category',
        data: models.map(function(m) { return m[0]; }),
        axisLabel: Object.assign({}, AXIS.axisLabel, { color: ink, fontSize: 12, fontFamily: 'WorkSans, PingFang SC, sans-serif' }),
        splitLine: { show: false } }),
      series: [{
        type: 'bar', barWidth: 18,
        data: models.map(function(m) { return { value: m[1], itemStyle: { color: m[2] ? accent : accent2, opacity: 0.92 } }; }),
        label: { show: true, position: 'right', color: ink, fontFamily: MONO, fontSize: 11.5 },
        markLine: {
          silent: true, symbol: 'none',
          lineStyle: { color: muted, type: 'dashed', width: 1.2 },
          label: { formatter: '中位 41', color: muted, fontFamily: MONO, fontSize: 10.5, position: 'insideEndTop' },
          data: [{ xAxis: 41 }]
        }
      }]
    });
    window.addEventListener('resize', function() { el.resize(); });
  })();

  /* ---------- 图 3 · 能力 × 价格二维地图 ---------- */
  (function() {
    var el = prep('chart-frontier', 470);
    if (!el) return;
    var openPts = [
      { value: [0.12, 74.0], name: 'Hy4 flash', lp: 'right', lb: [10, 2] },
      { value: [0.24, 83.5], name: 'GLM-5.3-Flash', lp: 'left', lb: [-8, 2] },
      { value: [0.26, 89.5], name: 'DeepSeek V4.1 Flash', lp: 'left', lb: [-10, 6] },
      { value: [0.52, 86.5], name: 'MiniMax M3', lp: 'top', lb: [0, 0] },
      { value: [0.63, 92.0], name: '混元 Hy4', lp: 'right', lb: [10, -4] },
      { value: [0.99, 81.0], name: 'DeepSeek V4 Pro', lp: 'right', lb: [10, 2] },
      { value: [2.11, 85.5], name: 'Qwen4-Max', lp: 'bottom', lb: [0, -4] },
      { value: [2.15, 87.0], name: 'GLM-5.3', lp: 'top', lb: [0, 4] },
      { value: [4.00, 91.5], name: 'Kimi K3.1 Pro', lp: 'top', lb: [4, 8] }
    ];
    var closedPts = [
      { value: [0.20, 82.0], name: 'Haiku 5.5', lp: 'left', lb: [-8, 2] },
      { value: [0.20, 62.5], name: 'GPT-6 Luna', lp: 'right', lb: [10, 2] },
      { value: [4.00, 88.5], name: 'Gemini 4 Carbon', lp: 'bottom', lb: [2, -12] },
      { value: [10.0, 87.5], name: 'Claude Opus 5.5', lp: 'left', lb: [-10, 2] },
      { value: [16.0, 86.0], name: 'GPT-6.2 Astra', lp: 'top', lb: [0, 6] },
      { value: [20.0, 88.0], name: 'Claude Fable 5', lp: 'top', lb: [0, 8] }
    ];
    function mk(d) {
      return d.map(function(p) {
        return { value: p.value, name: p.name,
          label: { show: true, position: p.lp, offset: p.lb || [0, 0], color: ink, fontSize: 11,
            fontFamily: 'WorkSans, PingFang SC, sans-serif', formatter: p.name } };
      });
    }
    el.setOption({
      animation: false,
      grid: { left: 14, right: 30, top: 24, bottom: 44, containLabel: true },
      tooltip: { appendToBody: true,
        formatter: function(p) { return p.data.name + '<br>能力分 <b>' + p.value[1] + '</b> · 混合价 <b>$' + p.value[0].toFixed(2) + '</b>'; } },
      xAxis: Object.assign({}, AXIS, { type: 'log', logBase: 10, min: 0.09, max: 28, name: '混合价 $/M（对数）',
        nameLocation: 'middle', nameGap: 28, nameTextStyle: { color: muted, fontSize: 11, fontFamily: 'WorkSans, PingFang SC, sans-serif' },
        axisLabel: Object.assign({}, AXIS.axisLabel, { formatter: function(v) { return (v >= 1 ? '$' + v : '$' + v.toFixed(2)); } }) }),
      yAxis: Object.assign({}, AXIS, { type: 'value', min: 58, max: 95, name: '能力分 /100',
        nameTextStyle: { color: muted, fontSize: 11, fontFamily: 'WorkSans, PingFang SC, sans-serif', padding: [0, 0, 0, 30] } }),
      series: [
        { type: 'line', silent: true, symbol: 'none', z: 1,
          data: [[0.12, 74.0], [0.26, 89.5], [0.63, 92.0]],
          lineStyle: { color: muted, type: 'dashed', width: 1.3 },
          tooltip: { show: false } },
        { name: '开放 / 开源阵营', type: 'scatter', symbolSize: 15, z: 3,
          data: mk(openPts), itemStyle: { color: accent, opacity: 0.9 } },
        { name: '闭源阵营', type: 'scatter', symbolSize: 15, z: 3,
          data: mk(closedPts), itemStyle: { color: accent2, opacity: 0.9 } }
      ]
    });
    window.addEventListener('resize', function() { el.resize(); });
  })();

  /* ---------- 图 4 · TB4.0 两个测量者 ---------- */
  (function() {
    var el = prep('chart-tb40', 430);
    if (!el) return;
    var cats = ['Claude Opus 5.5', 'Kimi K3.1 Pro', 'GPT-6 Astra', '混元 Hy4', 'DeepSeek-1017', 'Haiku 5.5', 'GLM-5.3-Flash', 'Hy4 flash', 'GPT-6 Luna'];
    var vals = [66.4, 65.7, 58.2, 44.4, 40.0, 36.4, 35.4, 19.2, 13.3];
    el.setOption({
      animation: false,
      grid: { left: 14, right: 20, top: 34, bottom: 76, containLabel: true },
      tooltip: { appendToBody: true, formatter: function(p) { return p.name + ' · TB4.0 <b>' + p.value + '%</b><br><span style="color:' + (p.dataIndex < 3 ? accent : accent2) + '">' + (p.dataIndex < 3 ? 'Kimi 自测口径' : '腾讯 Harness 口径') + '</span>'; } },
      xAxis: Object.assign({}, AXIS, { type: 'category', data: cats,
        axisLabel: Object.assign({}, AXIS.axisLabel, { color: ink, interval: 0, rotate: 32, fontSize: 11, fontFamily: 'WorkSans, PingFang SC, sans-serif' }) }),
      yAxis: Object.assign({}, AXIS, { type: 'value', max: 70 }),
      series: [{
        type: 'bar', barWidth: 26,
        data: vals.map(function(v, i) { return { value: v, itemStyle: { color: i < 3 ? accent : accent2, opacity: 0.92 } }; }),
        label: { show: true, position: 'top', color: ink, fontFamily: MONO, fontSize: 11 },
        markLine: { silent: true, symbol: 'none',
          lineStyle: { color: muted, type: 'dashed', width: 1.2 },
          label: { formatter: '口径分界', color: muted, fontSize: 10.5, fontFamily: 'WorkSans, PingFang SC, sans-serif' },
          data: [{ xAxis: 2.5 }] }
      }]
    });
    window.addEventListener('resize', function() { el.resize(); });
  })();
})();
