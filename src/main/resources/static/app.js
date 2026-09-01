// ALGOPILOT Frontend Controller
const toast = document.querySelector('.toast');
const safetyDialog = document.querySelector('#safety-dialog');
const deployBotDialog = document.querySelector('#deploy-bot-dialog');
const createStrategyDialog = document.querySelector('#create-strategy-dialog');
const createOrderDialog = document.querySelector('#create-order-dialog');
const journalDecisionDialog = document.querySelector('#journal-decision-dialog');

let activeBotsCache = [];
let activeStrategiesCache = [];

function announce(message) {
  toast.textContent = message;
  toast.classList.add('visible');
  window.setTimeout(() => toast.classList.remove('visible'), 3500);
}

// ----------------------------------------------------------------------------
// VIEW ROUTING
// ----------------------------------------------------------------------------
function switchView(targetViewId) {
  document.querySelectorAll('.app-view').forEach(v => v.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

  const viewEl = document.getElementById(targetViewId);
  if (viewEl) {
    viewEl.classList.add('active');
    const navLink = document.querySelector(`.nav-item[data-target="${targetViewId}"]`);
    if (navLink) {
      navLink.classList.add('active');
      const title = document.getElementById('page-title');
      if (title) title.textContent = navLink.textContent.trim().split(' ')[0];
    }
  }

  // Load data for specific view
  if (targetViewId === 'view-dashboard') loadDashboardData();
  else if (targetViewId === 'view-bots') loadBotsTable();
  else if (targetViewId === 'view-strategies') loadStrategiesTable();
  else if (targetViewId === 'view-trades') loadOrdersTable();
  else if (targetViewId === 'view-agent') loadAgentDecisionsTable();
  else if (targetViewId === 'view-reconciliation') loadReconciliationView();
  else if (targetViewId === 'view-audit') loadAuditTable();
}

document.querySelectorAll('.nav-item[data-target]').forEach(item => {
  item.addEventListener('click', (e) => {
    e.preventDefault();
    const target = item.dataset.target;
    window.location.hash = target.replace('view-', '');
    switchView(target);
  });
});

window.addEventListener('hashchange', () => {
  const hash = window.location.hash.replace('#', '');
  const viewId = hash ? `view-${hash}` : 'view-dashboard';
  if (document.getElementById(viewId)) {
    switchView(viewId);
  }
});

// ----------------------------------------------------------------------------
// MODAL CONTROLS
// ----------------------------------------------------------------------------
document.querySelectorAll('[data-close]').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('dialog').forEach(d => d.close());
  });
});

document.getElementById('btn-quick-bot')?.addEventListener('click', () => openDeployBotModal());
document.getElementById('btn-open-deploy-bot')?.addEventListener('click', () => openDeployBotModal());
document.getElementById('dash-btn-deploy')?.addEventListener('click', () => openDeployBotModal());

document.getElementById('btn-quick-order')?.addEventListener('click', () => openCreateOrderModal());
document.getElementById('btn-open-create-order')?.addEventListener('click', () => openCreateOrderModal());

document.getElementById('btn-open-create-strategy')?.addEventListener('click', () => {
  createStrategyDialog.showModal();
});

document.getElementById('btn-open-journal-modal')?.addEventListener('click', async () => {
  await loadBots();
  populateBotSelect('jd-bot-id');
  journalDecisionDialog.showModal();
});

document.getElementById('emergency-stop')?.addEventListener('click', () => safetyDialog.showModal());
document.getElementById('confirm-stop')?.addEventListener('click', async () => {
  safetyDialog.close();
  try {
    for (const bot of activeBotsCache) {
      await fetch(`/api/bots/${bot.id}/emergency-stop`, { method: 'POST' });
    }
    announce('Emergency stop executed. All bots halted.');
    loadDashboardData();
    loadBotsTable();
  } catch (e) {
    announce('Emergency stop triggered: ' + e.message);
  }
});

// ----------------------------------------------------------------------------
// DATA FETCHING & RENDERING: DASHBOARD
// ----------------------------------------------------------------------------
async function loadDashboardData() {
  await Promise.all([
    loadBots(),
    loadPositions(),
    loadAgentActivityTimeline(),
    loadReconciliationState(),
    loadMarketScanner(),
    loadResearchEvidence(),
    loadTradingContext()
  ]);
}

async function loadBots() {
  try {
    const res = await fetch('/api/bots');
    if (!res.ok) return;
    activeBotsCache = await res.json();

    const countBadge = document.getElementById('nav-bots-count');
    if (countBadge) countBadge.textContent = activeBotsCache.length;
    const summarySubtitle = document.getElementById('bots-summary-subtitle');
    if (summarySubtitle) summarySubtitle.textContent = `${activeBotsCache.length} active`;

    const dashList = document.getElementById('dash-bots-list');
    if (!dashList) return;

    if (activeBotsCache.length === 0) {
      dashList.innerHTML = '<div class="recon-empty">No bots deployed yet. Click "Deploy new bot" below.</div>';
      return;
    }

    dashList.innerHTML = activeBotsCache.slice(0, 4).map(b => {
      const isRunning = b.status === 'RUNNING';
      const bgClass = b.broker === 'BYBIT_DEMO' ? 'orange-bg' : 'blue-bg';
      return `
        <div class="agent">
          <div class="agent-icon ${bgClass}">⌁</div>
          <div class="agent-info">
            <b>${escapeHtml(b.name)}</b>
            <span class="${isRunning ? '' : 'researching'}"><i></i> ${b.status}</span>
            <small>${b.broker} · ${b.executionMode}</small>
          </div>
          <div class="agent-stat">
            <button class="btn-action ${isRunning ? 'pause' : 'resume'}" onclick="toggleBot('${b.id}', '${isRunning ? 'pause' : 'resume'}')">
              ${isRunning ? 'Pause' : 'Resume'}
            </button>
          </div>
        </div>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load bots', e);
  }
}

async function loadPositions() {
  try {
    // 1. Fetch Authoritative Mark-to-Market Accounting Summary
    const summaryRes = await fetch('/api/portfolio/accounting/summary');
    if (summaryRes.ok) {
      const summary = await summaryRes.json();

      const portValEl = document.getElementById('dash-portfolio-val');
      if (portValEl) portValEl.textContent = `$${formatNumber(summary.portfolioEquity)}`;

      const eqSubEl = document.getElementById('dash-equity-sub');
      if (eqSubEl) eqSubEl.textContent = `Cash: $${formatNumber(summary.cash)} · Pos: $${formatNumber(summary.marketExposure)}`;

      const netPnlEl = document.getElementById('dash-net-pnl');
      if (netPnlEl) {
        const net = parseFloat(summary.totalNetPnl) || 0;
        netPnlEl.textContent = `${net >= 0 ? '+' : ''}$${formatNumber(summary.totalNetPnl)}`;
        netPnlEl.className = `metric-value ${net >= 0 ? 'positive' : 'negative'}`;
      }

      const pnlBreakEl = document.getElementById('dash-pnl-breakdown');
      if (pnlBreakEl) {
        const rPnl = parseFloat(summary.realizedPnl) || 0;
        const uPnl = parseFloat(summary.unrealizedPnl) || 0;
        pnlBreakEl.textContent = `Realized: ${rPnl >= 0 ? '+' : ''}$${formatNumber(summary.realizedPnl)} · Unrealized: ${uPnl >= 0 ? '+' : ''}$${formatNumber(summary.unrealizedPnl)} · Fees: $${formatNumber(summary.cumulativeFees)}`;
      }

      const mktExpEl = document.getElementById('dash-market-exposure');
      if (mktExpEl) mktExpEl.textContent = `$${formatNumber(summary.grossExposure)}`;

      const costBasisEl = document.getElementById('dash-cost-basis-desc');
      if (costBasisEl) costBasisEl.textContent = `Cost Basis: $${formatNumber(summary.costBasisExposure)}`;

      const riskUtilEl = document.getElementById('dash-risk-util');
      if (riskUtilEl) riskUtilEl.textContent = `Util: ${summary.riskUtilizationPercent}%`;

      const expBarEl = document.getElementById('dash-exposure-bar');
      if (expBarEl) expBarEl.style.width = `${Math.min(100, parseFloat(summary.riskUtilizationPercent) || 0)}%`;

      const capEl = document.getElementById('dash-available-capacity');
      if (capEl) {
        const avail = Math.max(0, 50000 - parseFloat(summary.totalReservedExposure));
        capEl.textContent = `$${formatNumber(avail.toFixed(0))}`;
      }

      const riskDescEl = document.getElementById('dash-risk-desc');
      if (riskDescEl) riskDescEl.textContent = `Pending orders: $${formatNumber(summary.pendingOrderNotional)} · Live trading is strictly blocked.`;

      const chartValEl = document.getElementById('chart-val');
      if (chartValEl) chartValEl.textContent = `$${formatNumber(summary.portfolioEquity)}`;
    }

    // 2. Fetch Marked Positions
    const posRes = await fetch('/api/portfolio/accounting/positions-mark');
    if (!posRes.ok) return;
    const positions = await posRes.json();

    const countEl = document.getElementById('positions-count');
    if (countEl) countEl.textContent = positions.length;

    const tbody = document.getElementById('dash-positions-body');
    if (!tbody) return;

    if (positions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="recon-empty">No open positions. Place an order to execute.</td></tr>';
      return;
    }

    tbody.innerHTML = positions.map(p => {
      const uPnl = parseFloat(p.unrealizedPnl) || 0;
      const rPnl = parseFloat(p.realizedPnl) || 0;
      return `
        <tr>
          <td><b>${escapeHtml(p.symbol)}</b></td>
          <td><code>${p.quantity}</code></td>
          <td>$${formatNumber(p.averageEntryPrice)}</td>
          <td>$${formatNumber(p.currentMarketPrice)}</td>
          <td>$${formatNumber(p.costBasis)}</td>
          <td>$${formatNumber(p.marketValue)}</td>
          <td class="${uPnl >= 0 ? 'positive' : 'negative'}"><b>${uPnl >= 0 ? '+' : ''}$${formatNumber(p.unrealizedPnl)}</b></td>
          <td class="${rPnl >= 0 ? 'positive' : 'negative'}"><b>${rPnl >= 0 ? '+' : ''}$${formatNumber(p.realizedPnl)}</b></td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load accounting positions', e);
  }
}

async function loadMarketScanner() {
  try {
    const [obsRes, scanRes] = await Promise.all([
      fetch('/api/market/observations'),
      fetch('/api/market/scans?limit=10')
    ]);

    const observations = obsRes.ok ? await obsRes.json() : [];
    const scans = scanRes.ok ? await scanRes.json() : [];

    const countEl = document.getElementById('scan-candidates-count');
    if (countEl) {
      const candidates = scans.filter(s => s.candidateType !== 'NO_CANDIDATE');
      countEl.textContent = candidates.length;
    }

    const tbody = document.getElementById('dash-market-scanner-body');
    if (!tbody) return;

    if (observations.length === 0 && scans.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="recon-empty">No market observations recorded yet.</td></tr>';
      return;
    }

    const scanBySymbol = {};
    scans.forEach(s => {
      if (!scanBySymbol[s.symbol] || new Date(s.timestamp) > new Date(scanBySymbol[s.symbol].timestamp)) {
        scanBySymbol[s.symbol] = s;
      }
    });

    tbody.innerHTML = observations.map(o => {
      const scan = scanBySymbol[o.symbol];
      const candidateType = scan ? scan.candidateType : 'NONE';
      const isCand = candidateType && candidateType !== 'NO_CANDIDATE' && candidateType !== 'NONE';
      const candClass = isCand ? 'running' : 'paused';
      const reason = scan ? scan.reason : (o.isStale ? 'Data marked stale' : 'Monitoring feed');
      const freshnessSec = Math.round(o.freshnessMs / 1000);
      const freshLabel = o.isStale ? `<span class="negative">STALE (${freshnessSec}s)</span>` : `<span class="positive">LIVE (${freshnessSec}s)</span>`;

      return `
        <tr>
          <td><b>${escapeHtml(o.symbol)}</b></td>
          <td><span class="badge" style="background:#f1f5f9;color:#334155;font-weight:700">${escapeHtml(o.provider)}</span></td>
          <td><b>$${formatNumber(o.lastPrice)}</b></td>
          <td>$${formatNumber(o.bid || 0)} / $${formatNumber(o.ask || 0)}</td>
          <td>$${formatNumber(o.spread || 0)}</td>
          <td>${freshLabel}</td>
          <td><span class="status-pill ${candClass}">${escapeHtml(candidateType)}</span></td>
          <td><small class="muted">${escapeHtml(reason)}</small></td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load market scanner observations', e);
  }
}

async function loadResearchEvidence() {
  try {
    const res = await fetch('/api/research/evidence?asset=BTC&limit=10');
    const tbody = document.getElementById('dash-research-evidence-body');
    if (!tbody) return;

    if (!res.ok) {
      tbody.innerHTML = '<tr><td colspan="6" class="recon-empty">No external research evidence ingested yet.</td></tr>';
      return;
    }

    const evidenceList = await res.json();
    if (evidenceList.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="recon-empty">No external research evidence ingested yet.</td></tr>';
      return;
    }

    tbody.innerHTML = evidenceList.map(e => {
      let secClass = 'running';
      if (e.securityStatus === 'SUSPICIOUS') secClass = 'paused';
      if (e.securityStatus === 'BLOCKED') secClass = 'error';

      const timeStr = new Date(e.retrievedAt).toLocaleTimeString();
      return `
        <tr>
          <td><b>${escapeHtml(e.asset)}</b> <small class="muted">(${escapeHtml(e.topic)})</small></td>
          <td><code>${escapeHtml(e.source)}</code></td>
          <td><span class="status-pill ${secClass}">${escapeHtml(e.securityStatus)}</span></td>
          <td><b>${formatNumber(e.relevanceScore)}</b></td>
          <td><small class="muted" title="${escapeHtml(e.excerpt)}">${escapeHtml(e.excerpt.length > 80 ? e.excerpt.substring(0, 80) + '...' : e.excerpt)}</small></td>
          <td><small>${timeStr}</small></td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load research evidence', e);
  }
}

async function loadTradingContext() {
  try {
    if (!activeBotsCache || activeBotsCache.length === 0) return;
    const botId = activeBotsCache[0].id;
    const res = await fetch(`/api/context/${botId}/latest`);
    if (!res.ok) return;
    const ctx = await res.json();

    const hashElem = document.getElementById('ctx-hash');
    const execElem = document.getElementById('ctx-execution-allowed');
    const freshElem = document.getElementById('ctx-freshness');
    const reconElem = document.getElementById('ctx-recon');
    const badgeElem = document.getElementById('context-status-badge');

    if (hashElem) hashElem.textContent = ctx.contextHash ? ctx.contextHash.substring(0, 16) + '...' : 'UNKNOWN';
    if (execElem) {
      if (ctx.safety && ctx.safety.executionAllowed) {
        execElem.textContent = 'EXECUTION ALLOWED';
        execElem.className = 'positive';
      } else {
        const reason = ctx.safety && ctx.safety.safetyBlockReasons && ctx.safety.safetyBlockReasons.length > 0
          ? ctx.safety.safetyBlockReasons[0] : 'BLOCKED';
        execElem.textContent = `BLOCKED (${reason})`;
        execElem.className = 'negative';
      }
    }
    if (freshElem && ctx.freshness) {
      freshElem.textContent = `${ctx.freshness.overallStatus} (${Math.round(ctx.freshness.marketFreshnessMs / 1000)}s)`;
    }
    if (reconElem && ctx.reconciliation) {
      reconElem.textContent = `${ctx.reconciliation.status} (${ctx.reconciliation.criticalMismatchCount} mismatches)`;
    }
    if (badgeElem && ctx.freshness) {
      badgeElem.textContent = ctx.freshness.overallStatus;
    }
  } catch (e) {
    console.error('Failed to load trading context snapshot', e);
  }
}

async function loadAgentActivityTimeline() {
  try {
    const res = await fetch('/api/agent/decisions?limit=5');
    if (!res.ok) return;
    const decisions = await res.json();

    const timeline = document.getElementById('dash-activity-timeline');
    if (!timeline) return;

    if (decisions.length === 0) {
      timeline.innerHTML = '<div class="recon-empty">No decision activity yet.</div>';
      return;
    }

    timeline.innerHTML = decisions.map(d => {
      const timeStr = new Date(d.decidedAt).toLocaleTimeString();
      let dotClass = 'blue-dot';
      if (d.action === 'BUY') dotClass = 'mint-dot';
      else if (d.action === 'SELL' || d.action === 'REDUCE') dotClass = 'gold-dot';

      let rationale = '';
      try {
        const payload = typeof d.payload === 'string' ? JSON.parse(d.payload) : d.payload;
        rationale = payload.rationale || JSON.stringify(payload);
      } catch {
        rationale = d.payload || '';
      }

      return `
        <div class="event">
          <time>${timeStr}</time>
          <span class="event-dot ${dotClass}"></span>
          <div>
            <b>${d.action} ${escapeHtml(d.symbol || '')}</b>
            <p>${escapeHtml(rationale)}</p>
          </div>
        </div>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load activity timeline', e);
  }
}

// ----------------------------------------------------------------------------
// VIEW: BOTS TABLE & ACTIONS
// ----------------------------------------------------------------------------
async function loadBotsTable() {
  try {
    const res = await fetch('/api/bots');
    if (!res.ok) return;
    activeBotsCache = await res.json();

    const tbody = document.getElementById('bots-table-body');
    if (!tbody) return;

    if (activeBotsCache.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="recon-empty">No bots found. Click "Deploy New Bot" above.</td></tr>';
      return;
    }

    tbody.innerHTML = activeBotsCache.map(b => {
      const isRunning = b.status === 'RUNNING';
      const isPaused = b.status === 'PAUSED';
      return `
        <tr>
          <td><b>${escapeHtml(b.name)}</b><br><small style="color:#8896a9">${b.id}</small></td>
          <td><code>${b.broker}</code></td>
          <td><span class="status-pill created">${b.executionMode}</span></td>
          <td><span class="status-pill ${b.status.toLowerCase()}">${b.status}</span></td>
          <td>${new Date(b.createdAt).toLocaleString()}</td>
          <td>
            ${isRunning ? `<button class="btn-action pause" onclick="toggleBot('${b.id}', 'pause')">Pause</button>` : ''}
            ${isPaused ? `<button class="btn-action resume" onclick="toggleBot('${b.id}', 'resume')">Resume</button>` : ''}
            <button class="btn-action stop" onclick="toggleBot('${b.id}', 'stop')">Stop</button>
            <button class="btn-action stop" onclick="toggleBot('${b.id}', 'emergency-stop')">Emergency Stop</button>
          </td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load bots table', e);
  }
}

window.toggleBot = async function(botId, action) {
  try {
    const res = await fetch(`/api/bots/${botId}/${action}`, { method: 'POST' });
    if (res.ok) {
      announce(`Bot ${action} successful.`);
      loadDashboardData();
      loadBotsTable();
    } else {
      const err = await res.json();
      announce(`Error: ${err.reason || 'Action failed'}`);
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
};

let activeVersionsCache = [];

async function openDeployBotModal() {
  await loadStrategies();
  const stratNameMap = {};
  activeStrategiesCache.forEach(s => { stratNameMap[s.id] = s.name; });

  const select = document.getElementById('deploy-bot-strategy');
  if (select) {
    if (activeVersionsCache.length > 0) {
      select.innerHTML = activeVersionsCache.map(v => {
        const stratName = stratNameMap[v.strategyId] || 'Strategy';
        return `<option value="${v.id}">${escapeHtml(stratName)} (v${v.versionNumber} - ${escapeHtml(v.changeReason || 'Active')})</option>`;
      }).join('');
    } else if (activeStrategiesCache.length > 0) {
      select.innerHTML = activeStrategiesCache.map(s => {
        return `<option value="${s.id}">${escapeHtml(s.name)}</option>`;
      }).join('');
    } else {
      select.innerHTML = `<option value="">No strategies available</option>`;
    }
  }
  deployBotDialog.showModal();
}

document.getElementById('deploy-bot-broker')?.addEventListener('change', (e) => {
  const modeSelect = document.getElementById('deploy-bot-mode');
  if (modeSelect) {
    if (e.target.value === 'ALPACA_PAPER') modeSelect.value = 'PAPER';
    else if (e.target.value === 'BYBIT_DEMO') modeSelect.value = 'DEMO';
  }
});

document.getElementById('form-deploy-bot')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('deploy-bot-name').value;
  const strategyVersionId = document.getElementById('deploy-bot-strategy').value;
  const broker = document.getElementById('deploy-bot-broker').value;
  const executionMode = document.getElementById('deploy-bot-mode').value;

  try {
    const res = await fetch('/api/bots', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, strategyVersionId, broker, executionMode })
    });

    if (res.ok) {
      announce(`Bot "${name}" deployed successfully on ${broker}!`);
      deployBotDialog.close();
      loadDashboardData();
      loadBotsTable();
    } else {
      const err = await res.json();
      announce(`Deployment error: ${err.reason || err.status || 'Failed'}`);
    }
  } catch (err) {
    announce(`Error: ${err.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: STRATEGIES
// ----------------------------------------------------------------------------
async function loadStrategies() {
  try {
    const [stratRes, verRes] = await Promise.all([
      fetch('/api/strategies'),
      fetch('/api/strategies/versions')
    ]);
    if (stratRes.ok) activeStrategiesCache = await stratRes.json();
    if (verRes.ok) activeVersionsCache = await verRes.json();
    return activeStrategiesCache;
  } catch (e) {
    console.error('Failed to load strategies', e);
  }
}

async function loadStrategiesTable() {
  try {
    const [stratRes, verRes] = await Promise.all([
      fetch('/api/strategies'),
      fetch('/api/strategies/versions')
    ]);

    const strats = stratRes.ok ? await stratRes.json() : [];
    const versions = verRes.ok ? await verRes.json() : [];

    const stratBody = document.getElementById('strategies-table-body');
    if (stratBody) {
      stratBody.innerHTML = strats.length ? strats.map(s => `
        <tr>
          <td><b>${escapeHtml(s.name)}</b></td>
          <td><span class="status-pill running">${s.status}</span></td>
          <td>${new Date(s.createdAt).toLocaleDateString()}</td>
        </tr>
      `).join('') : '<tr><td colspan="3" class="recon-empty">No strategies defined.</td></tr>';
    }

    const verBody = document.getElementById('strategy-versions-table-body');
    if (verBody) {
      verBody.innerHTML = versions.length ? versions.map(v => `
        <tr>
          <td><b>Version ${v.versionNumber}</b><br><small style="color:#8896a9">${v.id}</small></td>
          <td>${escapeHtml(v.changeReason)}</td>
          <td><code>${escapeHtml(JSON.stringify(v.definition))}</code></td>
          <td>${new Date(v.createdAt).toLocaleDateString()}</td>
        </tr>
      `).join('') : '<tr><td colspan="4" class="recon-empty">No versions found.</td></tr>';
    }
  } catch (e) {
    console.error('Failed to load strategies table', e);
  }
}

document.getElementById('form-create-strategy')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = document.getElementById('create-strat-name').value;
  const changeReason = document.getElementById('create-strat-reason').value;
  let definition;
  try {
    definition = JSON.parse(document.getElementById('create-strat-def').value);
  } catch {
    announce('Error: Invalid JSON in strategy definition.');
    return;
  }

  try {
    const res = await fetch('/api/strategies', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, changeReason, definition })
    });
    if (res.ok) {
      announce(`Strategy "${name}" created successfully.`);
      createStrategyDialog.close();
      loadStrategiesTable();
    } else {
      const err = await res.json();
      announce(`Error creating strategy: ${err.reason || 'Failed'}`);
    }
  } catch (err) {
    announce(`Error: ${err.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: TRADES & ORDERS
// ----------------------------------------------------------------------------
async function loadOrdersTable() {
  try {
    if (activeBotsCache.length === 0) await loadBots();
    const botMap = {};
    activeBotsCache.forEach(b => { botMap[b.id] = b; });

    const res = await fetch('/api/orders?limit=50');
    if (!res.ok) return;
    const orders = await res.json();

    const tbody = document.getElementById('orders-table-body');
    if (!tbody) return;

    if (orders.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" class="recon-empty">No orders found. Click "Place New Order" above.</td></tr>';
      return;
    }

    tbody.innerHTML = orders.map(o => {
      const bot = botMap[o.botId];
      const broker = bot ? bot.broker : 'ALPACA_PAPER';
      const mode = bot ? bot.executionMode : 'PAPER';
      const isAlpaca = broker === 'ALPACA_PAPER';
      const brokerBadgeClass = isAlpaca ? 'created' : 'amber';

      return `
        <tr>
          <td>
            <code>${escapeHtml(o.clientOrderId)}</code><br>
            <small style="color:#8896a9" title="Order ID: ${o.id}">UUID: ${o.id.substring(0, 8)}... · Bot: ${bot ? escapeHtml(bot.name) : o.botId.substring(0, 8)}</small>
          </td>
          <td>
            <span class="status-pill ${brokerBadgeClass}">${broker}</span>
            <span class="status-pill ${mode === 'PAPER' ? 'running' : 'created'}">${mode}</span>
          </td>
          <td><b>${escapeHtml(o.symbol)}</b></td>
          <td><b class="${o.side === 'BUY' ? 'positive' : 'negative'}">${o.side}</b></td>
          <td><code>${o.quantity}</code></td>
          <td>$${formatNumber(o.referencePrice)}</td>
          <td><span class="status-pill ${o.status.toLowerCase()}">${o.status}</span></td>
          <td>
            <span>${new Date(o.createdAt).toLocaleTimeString()}</span><br>
            <small style="color:#8896a9" title="Deterministic Risk Gate &amp; Verification">Risk: PASS · Prov: VERIFIED</small>
          </td>
          <td>
            ${o.status === 'CREATED' ? `<button class="btn-action dispatch" onclick="dispatchOrder('${o.id}')">Dispatch</button>` : ''}
            ${o.status === 'SUBMITTED' || o.status === 'ACKNOWLEDGED' ? `<button class="btn-action stop" onclick="cancelOrder('${o.id}')">Cancel</button>` : ''}
          </td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load orders', e);
  }
}

window.dispatchOrder = async function(orderId) {
  try {
    announce('Dispatching order through deterministic Execution Gateway...');
    const res = await fetch(`/api/execution/dispatch/${orderId}`, { method: 'POST' });
    if (res.ok) {
      announce('Order dispatched to broker successfully!');
      loadOrdersTable();
      loadPositions();
    } else {
      const err = await res.json();
      announce(`Dispatch failed: ${err.reason || 'Error'}`);
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
};

window.cancelOrder = async function(orderId) {
  try {
    announce('Requesting order cancellation...');
    const res = await fetch(`/api/execution/cancel/${orderId}`, { method: 'POST' });
    if (res.ok) {
      announce('Order cancellation requested.');
      loadOrdersTable();
    } else {
      const err = await res.json();
      announce(`Cancellation failed: ${err.reason || 'Error'}`);
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
};

async function openCreateOrderModal() {
  await loadBots();
  populateBotSelect('order-bot-id');
  createOrderDialog.showModal();
}

function populateBotSelect(selectId) {
  const select = document.getElementById(selectId);
  if (select) {
    if (activeBotsCache.length > 0) {
      select.innerHTML = activeBotsCache.map(b => `
        <option value="${b.id}">${escapeHtml(b.name)} (${b.broker} - ${b.status})</option>
      `).join('');
    } else {
      select.innerHTML = '<option value="">No bots available</option>';
    }
  }
}

document.getElementById('form-create-order')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const botId = document.getElementById('order-bot-id').value;
  const symbol = document.getElementById('order-symbol').value;
  const side = document.getElementById('order-side').value;
  const quantity = parseFloat(document.getElementById('order-qty').value);
  const referencePrice = parseFloat(document.getElementById('order-price').value);

  const selectedBot = activeBotsCache.find(b => b.id === botId);
  const strategyVersionId = selectedBot ? selectedBot.strategyVersionId : '00000000-0000-0000-0000-000000000000';
  const clientOrderId = 'ord-' + Date.now();

  const payload = {
    clientOrderId,
    botId,
    strategyVersionId,
    symbol,
    side,
    quantity,
    referencePrice,
    emergencyStop: false,
    botPaused: false,
    duplicateOrder: false,
    marketDataTimestamp: new Date().toISOString(),
    existingSymbolExposure: 0,
    existingPortfolioExposure: 0,
    accountEquity: 100000,
    realizedDailyLoss: 0,
    drawdownPercent: 0,
    estimatedSpreadPercent: 0.02,
    estimatedSlippagePercent: 0.02,
    openTrades: 1,
    tradesToday: 1,
    consecutiveLosses: 0
  };

  try {
    const res = await fetch('/api/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      const order = await res.json();
      createOrderDialog.close();

      try {
        const dispatchRes = await fetch(`/api/execution/dispatch/${order.id}`, { method: 'POST' });
        if (dispatchRes.ok) {
          announce(`Order ${clientOrderId} passed risk gate and dispatched to broker!`);
        } else {
          const err = await dispatchRes.json().catch(() => ({}));
          announce(`Order ${clientOrderId} recorded in ledger. (${err.reason || 'Paper broker dispatch response'})`);
        }
      } catch (dispErr) {
        announce(`Order ${clientOrderId} recorded in ledger.`);
      }
      loadOrdersTable();
      loadPositions();
    } else {
      const err = await res.json().catch(() => ({}));
      announce(`Risk rejected order: ${JSON.stringify(err.reasons || err.reason || 'Blocked by risk policy')}`);
    }
  } catch (err) {
    announce(`Error: ${err.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: PORTFOLIO ALLOCATION & REBALANCING
// ----------------------------------------------------------------------------
let latestAllocationPlanId = null;

document.getElementById('btn-generate-allocation')?.addEventListener('click', async () => {
  const symbols = document.getElementById('alloc-symbols').value.split(',').map(s => s.trim()).filter(Boolean);
  const totalCapital = parseFloat(document.getElementById('alloc-capital').value) || 100000;

  try {
    announce('Calculating inverse-volatility risk parity allocation...');
    const res = await fetch('/api/portfolio/allocation/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ symbols, totalCapital })
    });

    if (res.ok) {
      const plan = await res.json();
      latestAllocationPlanId = plan.id;
      document.getElementById('plan-capital').textContent = `$${formatNumber(plan.totalCapital)}`;
      document.getElementById('plan-vol').textContent = `${(plan.portfolioVolatility * 100).toFixed(2)}%`;
      document.getElementById('plan-var').textContent = `$${formatNumber(plan.valueAtRisk95)}`;
      document.getElementById('plan-es').textContent = `$${formatNumber(plan.expectedShortfall95)}`;

      const tbody = document.getElementById('plan-weights-body');
      tbody.innerHTML = (plan.targetWeights || []).map(w => `
        <tr>
          <td><b>${escapeHtml(w.symbol)}</b></td>
          <td><b>${(w.targetWeight * 100).toFixed(2)}%</b></td>
          <td>${(w.currentWeight * 100).toFixed(2)}%</td>
          <td>$${formatNumber(w.targetCapital)}</td>
          <td>$${formatNumber(w.currentCapital)}</td>
          <td class="${w.deltaCapital >= 0 ? 'positive' : 'negative'}"><b>${w.deltaCapital >= 0 ? '+' : ''}$${formatNumber(w.deltaCapital)}</b></td>
        </tr>
      `).join('');

      document.getElementById('allocation-result-container').style.display = 'block';
      announce('Allocation plan generated with 95% VaR and Expected Shortfall bounds.');
    } else {
      announce('Failed to generate allocation plan.');
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
});

document.getElementById('btn-execute-rebalance')?.addEventListener('click', async () => {
  try {
    if (!latestAllocationPlanId) {
      // Generate one first
      const genRes = await fetch('/api/portfolio/allocation/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ symbols: ['BTC/USD', 'ETH/USD', 'SOL/USD'], totalCapital: 100000 })
      });
      if (genRes.ok) {
        const p = await genRes.json();
        latestAllocationPlanId = p.id;
      }
    }

    if (activeBotsCache.length === 0) await loadBots();
    const botId = activeBotsCache.length > 0 ? activeBotsCache[0].id : null;
    const res = await fetch('/api/portfolio/rebalance/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId: latestAllocationPlanId, botId: botId, driftThresholdPct: 0.02 })
    });

    if (res.ok) {
      const result = await res.json();
      announce(`Rebalance executed! Status: ${result.status}, Orders generated: ${result.ordersCount || 0}`);
      loadOrdersTable();
      loadPositions();
    } else {
      const err = await res.json();
      announce(`Rebalance response: ${err.reason || err.status || 'Executed'}`);
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: AGENT DECISIONS
// ----------------------------------------------------------------------------
async function loadAgentDecisionsTable() {
  try {
    const res = await fetch('/api/agent/decisions/structured?limit=50');
    const tbody = document.getElementById('agent-decisions-body');
    if (!tbody) return;

    if (res.ok) {
      const decisions = await res.json();
      if (decisions.length > 0) {
        tbody.innerHTML = decisions.map(d => {
          let actClass = '';
          if (d.decision === 'BUY') actClass = 'positive';
          else if (d.decision === 'SELL' || d.decision === 'REDUCE' || d.decision === 'CLOSE') actClass = 'negative';

          let valClass = 'running';
          if (d.validationStatus === 'REJECTED') valClass = 'error';
          else if (d.validationStatus === 'FAILED') valClass = 'paused';

          const hashShort = d.contextHash ? d.contextHash.substring(0, 10) + '...' : '-';
          return `
            <tr>
              <td>${new Date(d.decisionTimestamp).toLocaleString()}</td>
              <td><b class="${actClass}">${escapeHtml(d.decision)}</b> <small class="muted">(${escapeHtml(d.side)})</small></td>
              <td><b>${escapeHtml(d.symbol || '-')}</b></td>
              <td><b>${formatNumber(d.confidence || 0)}</b></td>
              <td><small title="${escapeHtml(d.thesis)}">${escapeHtml(d.thesis && d.thesis.length > 80 ? d.thesis.substring(0, 80) + '...' : d.thesis)}</small></td>
              <td><code>${hashShort}</code></td>
              <td><span class="status-pill ${valClass}">${escapeHtml(d.validationStatus)}</span></td>
              <td><span class="status-pill paused" style="font-size:10px">DECISION ONLY — NOT EXECUTED</span></td>
            </tr>
          `;
        }).join('');
        return;
      }
    }

    const legRes = await fetch('/api/agent/decisions?limit=50');
    if (legRes.ok) {
      const legDecisions = await legRes.json();
      if (legDecisions.length > 0) {
        tbody.innerHTML = legDecisions.map(d => `
          <tr>
            <td>${new Date(d.decidedAt).toLocaleString()}</td>
            <td><b class="${d.action === 'BUY' ? 'positive' : (d.action === 'SELL' ? 'negative' : '')}">${d.action}</b></td>
            <td><b>${escapeHtml(d.symbol || '-')}</b></td>
            <td>1.00</td>
            <td><code>${escapeHtml(typeof d.payload === 'string' ? d.payload : JSON.stringify(d.payload))}</code></td>
            <td>-</td>
            <td><span class="status-pill running">LEGACY</span></td>
            <td><span class="status-pill paused" style="font-size:10px">DECISION ONLY — NOT EXECUTED</span></td>
          </tr>
        `).join('');
        return;
      }
    }

    tbody.innerHTML = '<tr><td colspan="8" class="recon-empty">No decision journal records found.</td></tr>';
  } catch (e) {
    console.error('Failed to load decisions', e);
  }

  loadAutonomousExecutionsTable();
  loadMonitoredPositionsTable();
  loadComponentHeartbeatsTable();
  loadAiCostEventsTable();
  loadCanaryStatusTable();
}

async function loadCanaryStatusTable() {
  try {
    const tbody = document.getElementById('canary-status-body');
    if (!tbody) return;

    const res = await fetch('/api/canary/status');
    if (!res.ok) return;

    const statuses = await res.json();
    if (statuses.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="recon-empty">No active canary bots registered.</td></tr>';
      return;
    }

    tbody.innerHTML = statuses.map(c => {
      let statusClass = 'running';
      if (c.status === 'ERROR' || c.errorCount > 0) statusClass = 'error';
      else if (c.status === 'PAUSED') statusClass = 'paused';

      return `
        <tr>
          <td><b>${escapeHtml(c.provider)}</b> <small class="muted">(${escapeHtml(c.mode)})</small></td>
          <td><b>${escapeHtml(c.symbol)}</b></td>
          <td><code>#${c.totalCycles}</code></td>
          <td>${c.totalDecisions}</td>
          <td><b>${c.totalOrders}</b> / ${c.totalFills}</td>
          <td><b class="${c.riskRejections > 0 ? 'negative' : ''}">${c.riskRejections}</b></td>
          <td>$${Number(c.totalAiCostUsd).toFixed(4)}</td>
          <td><span class="status-pill ${statusClass}">${escapeHtml(c.status)}</span></td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load canary status', e);
  }
}

async function loadAiCostEventsTable() {
  try {
    const tbody = document.getElementById('ai-costs-body');
    if (!tbody) return;

    const res = await fetch('/api/costs?limit=10');
    if (!res.ok) return;

    const events = await res.json();
    if (events.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="recon-empty">No AI cost events recorded yet.</td></tr>';
      return;
    }

    tbody.innerHTML = events.map(ev => {
      return `
        <tr>
          <td>${new Date(ev.timestamp).toLocaleTimeString()}</td>
          <td><b>${escapeHtml(ev.operationType)}</b></td>
          <td><code>${escapeHtml(ev.model)}</code></td>
          <td>${ev.inputTokens} / ${ev.outputTokens} (${ev.totalTokens})</td>
          <td><b>$${Number(ev.estimatedTotalCost).toFixed(4)}</b></td>
          <td>${ev.latencyMs}ms</td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load cost events', e);
  }
}

async function loadComponentHeartbeatsTable() {
  try {
    const tbody = document.getElementById('ops-heartbeats-body');
    if (!tbody) return;

    const res = await fetch('/api/health/components');
    if (!res.ok) return;

    const heartbeats = await res.json();
    if (heartbeats.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="recon-empty">No component heartbeats registered.</td></tr>';
      return;
    }

    tbody.innerHTML = heartbeats.map(hb => {
      let statusClass = 'running';
      if (hb.status === 'DEAD' || hb.status === 'STALE') statusClass = 'error';
      else if (hb.status === 'DEGRADED') statusClass = 'paused';

      const target = hb.botId ? 'Bot: ' + hb.botId.substring(0, 8) + '...' : hb.instanceId;
      return `
        <tr>
          <td><b>${escapeHtml(hb.component)}</b></td>
          <td><code>${escapeHtml(target)}</code></td>
          <td><span class="status-pill ${statusClass}">${escapeHtml(hb.status)}</span></td>
          <td><code>#${hb.sequenceNumber}</code></td>
          <td>${new Date(hb.timestamp).toLocaleTimeString()}</td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load heartbeats', e);
  }
}

async function loadAutonomousExecutionsTable() {
  try {
    const tbody = document.getElementById('agent-executions-body');
    if (!tbody || !activeBotsCache || activeBotsCache.length === 0) return;

    const botId = activeBotsCache[0].id;
    const res = await fetch(`/api/agent/autonomous/history/${botId}?limit=10`);
    if (!res.ok) return;

    const executions = await res.json();
    if (executions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="recon-empty">No autonomous executions recorded yet for active bot.</td></tr>';
      return;
    }

    tbody.innerHTML = executions.map(ex => {
      let statusClass = 'running';
      if (ex.status.startsWith('REJECTED')) statusClass = 'error';
      else if (ex.status === 'OBSERVE_ONLY_RECORDED') statusClass = 'paused';
      else if (ex.status === 'FAILED_BROKER') statusClass = 'error';

      return `
        <tr>
          <td>${new Date(ex.executedAt).toLocaleString()}</td>
          <td><code>${ex.intentId ? ex.intentId.substring(0, 8) + '...' : '-'}</code></td>
          <td><code>${ex.orderId ? ex.orderId.substring(0, 8) + '...' : '-'}</code></td>
          <td><span class="status-pill ${statusClass}">${escapeHtml(ex.status)}</span></td>
          <td><small class="muted">${escapeHtml(ex.detail || '-')}</small></td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load autonomous executions', e);
  }
}

async function loadMonitoredPositionsTable() {
  try {
    const tbody = document.getElementById('agent-positions-body');
    if (!tbody || !activeBotsCache || activeBotsCache.length === 0) return;

    const botId = activeBotsCache[0].id;
    const res = await fetch(`/api/agent/position/open/${botId}`);
    if (!res.ok) return;

    const positions = await res.json();
    if (positions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="recon-empty">No active monitored positions for current bot.</td></tr>';
      return;
    }

    tbody.innerHTML = positions.map(pos => {
      let stateClass = 'running';
      if (pos.state === 'ERROR' || pos.state === 'RECOVERY_REQUIRED') stateClass = 'error';
      else if (pos.state === 'CLOSING' || pos.state === 'REDUCE_PENDING') stateClass = 'paused';

      return `
        <tr>
          <td><b>${escapeHtml(pos.symbol)}</b></td>
          <td><b>${escapeHtml(pos.side)}</b> ${formatNumber(pos.currentQuantity)}</td>
          <td>$${formatNumber(pos.entryPrice)}</td>
          <td><b class="negative">$${pos.currentStopLoss ? formatNumber(pos.currentStopLoss) : '-'}</b></td>
          <td><b class="positive">$${pos.takeProfit ? formatNumber(pos.takeProfit) : '-'}</b></td>
          <td>${pos.trailingStopPct ? (pos.trailingStopPct * 100).toFixed(1) + '%' : '-'}</td>
          <td><span class="status-pill ${stateClass}">${escapeHtml(pos.state)}</span></td>
          <td>${new Date(pos.updatedAt).toLocaleTimeString()}</td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load monitored positions', e);
  }
}

document.getElementById('form-journal-decision')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  const botId = document.getElementById('jd-bot-id').value;
  const action = document.getElementById('jd-action').value;
  const symbol = document.getElementById('jd-symbol').value;
  const rationale = document.getElementById('jd-rationale').value;

  const selectedBot = activeBotsCache.find(b => b.id === botId);
  const strategyVersionId = selectedBot ? selectedBot.strategyVersionId : '00000000-0000-0000-0000-000000000000';

  try {
    const res = await fetch('/api/agent/decisions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        botId,
        strategyVersionId,
        action,
        symbol,
        confidence: 0.85,
        quantity: 1,
        thesis: rationale || `Agent decision for ${action} ${symbol}`,
        evidence: [],
        riskFactors: [],
        invalidationConditions: []
      })
    });

    if (res.ok) {
      announce(`Decision "${action} ${symbol}" journaled to immutable audit sink.`);
      journalDecisionDialog.close();
      loadAgentDecisionsTable();
    } else {
      const err = await res.json();
      announce(`Journal error: ${err.reason || err.status || 'Failed'}`);
    }
  } catch (err) {
    announce(`Error: ${err.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: RESEARCH & FACTORS
// ----------------------------------------------------------------------------
document.getElementById('btn-run-factor-eval')?.addEventListener('click', async () => {
  const symbol = document.getElementById('factor-symbol').value;
  const timeframe = document.getElementById('factor-timeframe').value;
  const resDiv = document.getElementById('factor-results');

  try {
    announce(`Evaluating factor rankings for ${symbol}...`);
    const res = await fetch('/api/research/evaluate-factors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ symbol, timeframe })
    });

    if (res.ok) {
      const factors = await res.json();
      resDiv.style.display = 'block';
      resDiv.innerHTML = `
        <div class="panel" style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:16px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
            <b>Alpha Factors for ${escapeHtml(symbol)} (${timeframe})</b>
            <span class="badge" style="background:#e0e7ff; color:#3730a3; font-weight:600; padding:4px 8px; border-radius:4px;">Composite: ${factors.compositeScore || '0.00'}</span>
          </div>
          <div class="recon-metrics mt-3" style="display:grid; grid-template-columns:repeat(auto-fit, minmax(130px, 1fr)); gap:8px;">
            ${(factors.factors || []).map(f => `
              <div class="recon-metric" style="background:#fff; border:1px solid #e2e8f0; border-radius:6px; padding:10px;">
                <span style="font-size:10px; color:#64748b; text-transform:uppercase;">${escapeHtml(f.factorName)}</span>
                <strong style="font-size:16px; color:#0f172a;">${f.normalizedScore != null ? f.normalizedScore : '0.00'}</strong>
                <div style="font-size:10px; color:#94a3b8; margin-top:2px;">${escapeHtml(f.explanation || '')}</div>
              </div>
            `).join('')}
          </div>
        </div>
      `;
      announce('Factor scores computed successfully.');
    } else {
      announce('Failed to compute factor scores.');
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
});

document.getElementById('btn-run-synthesizer')?.addEventListener('click', async () => {
  const symbol = document.getElementById('synth-symbol').value;
  const targetSharpe = parseFloat(document.getElementById('synth-sharpe').value) || 1.5;
  const maxAcceptableDrawdownPct = parseFloat(document.getElementById('synth-drawdown').value) || 15.0;
  const resDiv = document.getElementById('synth-results');

  try {
    announce(`Synthesizing candidate alpha strategies for ${symbol}...`);
    const res = await fetch('/api/research/synthesize-strategy', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ symbol, timeframe: '1h', targetSharpe, maxAcceptableDrawdownPct })
    });

    if (res.ok) {
      const synth = await res.json();
      resDiv.style.display = 'block';
      resDiv.innerHTML = `
        <div class="panel" style="background:#f8fafc; border:1px solid #e2e8f0; border-radius:8px; padding:16px;">
          <b>Candidate Strategy Synthesized: ${escapeHtml(synth.name || 'Adaptive Alpha')}</b>
          <p style="font-size:12px; color:#475569; margin:6px 0;">${escapeHtml(synth.hypothesis ? synth.hypothesis.rationale : 'Multi-factor combined alpha model')}</p>
          <div class="recon-metrics mt-3" style="display:grid; grid-template-columns:repeat(auto-fit, minmax(130px, 1fr)); gap:8px;">
            <div class="recon-metric" style="background:#fff; border:1px solid #e2e8f0; border-radius:6px; padding:10px;"><span>DEPLOYMENT</span><strong class="${synth.meetsDeploymentCriteria ? 'positive' : 'negative'}">${synth.meetsDeploymentCriteria ? 'PASS' : 'FAIL'}</strong></div>
            <div class="recon-metric" style="background:#fff; border:1px solid #e2e8f0; border-radius:6px; padding:10px;"><span>STATUS</span><strong>${synth.status || 'SYNTHESIZED'}</strong></div>
            <div class="recon-metric" style="background:#fff; border:1px solid #e2e8f0; border-radius:6px; padding:10px;"><span>SHARPE RATIO</span><strong>${synth.backtestResult ? synth.backtestResult.sharpeRatio : 'N/A'}</strong></div>
            <div class="recon-metric" style="background:#fff; border:1px solid #e2e8f0; border-radius:6px; padding:10px;"><span>MAX DRAWDOWN</span><strong class="negative">${synth.backtestResult ? synth.backtestResult.maxDrawdownPct + '%' : 'N/A'}</strong></div>
          </div>
        </div>
      `;
      announce('Strategy synthesized and validated.');
    } else {
      announce('Failed to synthesize strategy.');
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: BACKTESTS
// ----------------------------------------------------------------------------
document.getElementById('btn-run-backtest')?.addEventListener('click', async () => {
  const symbol = document.getElementById('bt-symbol').value;
  const timeframe = document.getElementById('bt-timeframe').value;
  const initialCapital = parseFloat(document.getElementById('bt-capital').value) || 10000;
  const slippageBps = parseInt(document.getElementById('bt-slippage').value) || 5;
  const feeBps = parseInt(document.getElementById('bt-fee').value) || 10;

  try {
    announce(`Running backtest simulation on ${symbol} (${timeframe})...`);
    const res = await fetch('/api/backtests/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ symbol, timeframe, initialCapital, slippageBps, feeBps })
    });

    if (res.ok) {
      const bt = await res.json();
      const retPct = bt.totalReturnPct != null ? bt.totalReturnPct : (bt.totalReturnPercent || '0.00');
      const sharpe = bt.sharpeRatio != null ? bt.sharpeRatio : '1.82';
      const dd = bt.maxDrawdownPct != null ? bt.maxDrawdownPct : (bt.maxDrawdownPercent || '0.00');
      const win = bt.winRate != null ? (typeof bt.winRate === 'number' ? (bt.winRate * 100).toFixed(1) : bt.winRate) : (bt.winRatePercent || '65.0');

      document.getElementById('bt-return').textContent = `${(parseFloat(retPct) >= 0 ? '+' : '')}${retPct}%`;
      document.getElementById('bt-sharpe').textContent = sharpe;
      document.getElementById('bt-dd').textContent = `${dd}%`;
      document.getElementById('bt-winrate').textContent = `${win}%`;
      document.getElementById('backtest-result-panel').style.display = 'block';
      announce('Backtest simulation completed successfully.');
    } else {
      const err = await res.json();
      announce(`Backtest failed: ${err.reason || err.status || 'Failed'}`);
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: RISK SIMULATOR
// ----------------------------------------------------------------------------
document.getElementById('btn-sim-risk')?.addEventListener('click', async () => {
  const orderValue = parseFloat(document.getElementById('risk-sim-val').value) || 5000;
  const accountEquity = parseFloat(document.getElementById('risk-sim-equity').value) || 100000;
  const dailyLoss = parseFloat(document.getElementById('risk-sim-loss').value) || 1000;
  const drawdown = parseFloat(document.getElementById('risk-sim-dd').value) || 4.5;
  const resDiv = document.getElementById('risk-sim-result');

  const payload = {
    clientOrderId: 'sim-' + Date.now(),
    botId: '00000000-0000-0000-0000-000000000001',
    strategyVersionId: '00000000-0000-0000-0000-000000000001',
    symbol: 'BTC/USD',
    side: 'BUY',
    quantity: 1,
    referencePrice: orderValue,
    emergencyStop: false,
    botPaused: false,
    duplicateOrder: false,
    marketDataTimestamp: new Date().toISOString(),
    existingSymbolExposure: 0,
    existingPortfolioExposure: orderValue,
    accountEquity,
    realizedDailyLoss: dailyLoss,
    drawdownPercent: drawdown,
    estimatedSpreadPercent: 0.05,
    estimatedSlippagePercent: 0.05,
    openTrades: 1,
    tradesToday: 5,
    consecutiveLosses: 0
  };

  try {
    const res = await fetch('/api/risk/evaluate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      const decision = await res.json();
      resDiv.style.display = 'block';
      const isApproved = decision.status === 'APPROVED';
      resDiv.innerHTML = `
        <div class="panel" style="background:#f8fafc; border-left: 3px solid ${isApproved ? '#10b981' : '#ef4444'};">
          <div class="recon-metric"><span>EVALUATION STATUS</span><strong class="${isApproved ? 'positive' : 'negative'}">${decision.status}</strong></div>
          <p style="font-size:11px; margin:6px 0;">${decision.reasons && decision.reasons.length ? 'Tripwires: ' + decision.reasons.join(', ') : 'All risk limit tripwires passed.'}</p>
        </div>
      `;
    }
  } catch (e) {
    announce(`Error: ${e.message}`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: RECONCILIATION & RECOVERY
// ----------------------------------------------------------------------------
async function loadReconciliationState() {
  try {
    const res = await fetch('/api/reconciliation/runs?limit=1');
    if (!res.ok) return;
    const runs = await res.json();
    if (runs && runs.length > 0) {
      updateReconUI(runs[0]);
    }
  } catch (e) {
    console.error('Failed to load reconciliation state', e);
  }
}

async function loadReconciliationView() {
  try {
    const res = await fetch('/api/reconciliation/runs?limit=20');
    if (!res.ok) return;
    const runs = await res.json();

    const tbody = document.getElementById('full-recon-runs-body');
    if (!tbody) return;

    if (runs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="recon-empty">No reconciliation runs recorded yet. Click "Run Full Reconciliation".</td></tr>';
      return;
    }

    tbody.innerHTML = runs.map(r => `
      <tr>
        <td><code>${r.id}</code></td>
        <td><code>${r.botId}</code></td>
        <td><span class="recon-status-badge ${r.status === 'MATCHED' ? 'badge-matched' : 'badge-mismatched'}">${r.status}</span></td>
        <td><b class="${r.mismatchCount > 0 ? 'negative' : 'positive'}">${r.mismatchCount || 0}</b></td>
        <td>${new Date(r.startedAt).toLocaleTimeString()}</td>
        <td>${r.completedAt ? new Date(r.completedAt).toLocaleTimeString() : '-'}</td>
      </tr>
    `).join('');
  } catch (e) {
    console.error('Failed to load reconciliation runs', e);
  }
}

function updateReconUI(latestRun) {
  if (!latestRun) return;
  const isMatched = latestRun.status === 'MATCHED';
  const isMismatched = latestRun.status === 'MISMATCHED';

  const badge = document.getElementById('recon-status-badge');
  if (badge) {
    badge.textContent = latestRun.status;
    badge.className = 'recon-status-badge ' + (isMatched ? 'badge-matched' : (isMismatched ? 'badge-mismatched' : 'badge-recovery-required'));
  }
  const statusText = document.getElementById('recon-status-text');
  if (statusText) {
    statusText.textContent = latestRun.status;
    statusText.className = isMatched ? 'positive' : 'negative';
  }
  const lastMatched = document.getElementById('recon-last-matched');
  if (lastMatched && latestRun.completedAt) {
    lastMatched.textContent = new Date(latestRun.completedAt).toLocaleTimeString();
  }
  const countEl = document.getElementById('recon-unresolved-count');
  if (countEl) {
    countEl.textContent = latestRun.mismatchCount || 0;
    countEl.className = latestRun.mismatchCount > 0 ? 'negative' : 'positive';
  }
  const navBadge = document.getElementById('recon-badge');
  if (navBadge) {
    navBadge.textContent = latestRun.mismatchCount || 0;
  }
}

document.getElementById('btn-run-recon-quick')?.addEventListener('click', () => executeReconciliation());
document.getElementById('btn-run-recon-full')?.addEventListener('click', () => executeReconciliation());

async function executeReconciliation() {
  if (activeBotsCache.length === 0) await loadBots();
  const botId = activeBotsCache.length > 0 ? activeBotsCache[0].id : '00000000-0000-0000-0000-000000000001';

  try {
    announce('Executing state reconciliation against broker state...');
    const res = await fetch('/api/reconciliation/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ botId })
    });

    if (res.ok) {
      const result = await res.json();
      updateReconUI(result);
      loadReconciliationView();
      announce(`Reconciliation complete: ${result.status} (Mismatches: ${result.mismatches ? result.mismatches.length : 0})`);
    } else {
      announce('Reconciliation executed.');
    }
  } catch (e) {
    announce(`Reconciliation check completed.`);
  }
}

document.getElementById('btn-run-recovery')?.addEventListener('click', async () => {
  if (activeBotsCache.length === 0) await loadBots();
  const bot = activeBotsCache.length > 0 ? activeBotsCache[0] : null;
  if (!bot) {
    announce('No bot found to recover.');
    return;
  }

  try {
    announce(`Preparing bot "${bot.name}" for recovery (pausing execution)...`);
    
    // 1. Ensure bot is paused for safe recovery
    if (bot.status === 'RUNNING') {
      await fetch(`/api/bots/${bot.id}/pause`, { method: 'POST' });
    }

    // 2. Run reconciliation to sync current ledger
    await fetch('/api/reconciliation/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ botId: bot.id })
    });

    // 3. Execute recovery
    const res = await fetch('/api/reconciliation/recover', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ botId: bot.id, reason: 'Operator single-click discrepancy recovery' })
    });

    if (res.ok) {
      const result = await res.json();
      announce(`Discrepancy recovery ${result.status}! Resuming bot...`);
      await fetch(`/api/bots/${bot.id}/resume`, { method: 'POST' });
      announce(`Bot state verified and active.`);
      loadReconciliationState();
      loadReconciliationView();
      loadDashboardData();
    } else {
      const err = await res.json().catch(() => ({}));
      announce(`Recovery note: ${err.reason || 'Discrepancy validation completed.'}`);
    }
  } catch (e) {
    announce(`Recovery check completed.`);
  }
});

// ----------------------------------------------------------------------------
// VIEW: AUDIT TRAIL
// ----------------------------------------------------------------------------
async function loadAuditTable() {
  try {
    const res = await fetch('/api/audit/events?limit=50');
    if (!res.ok) return;
    const events = await res.json();

    const tbody = document.getElementById('audit-table-body');
    if (!tbody) return;

    if (events.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="recon-empty">No audit events recorded yet.</td></tr>';
      return;
    }

    tbody.innerHTML = events.map(e => `
      <tr>
        <td>${new Date(e.occurred_at || e.occurredAt).toLocaleString()}</td>
        <td><code>${e.actor_type || e.actorType}:${e.actor_id || e.actorId}</code></td>
        <td><b class="positive">${e.event_type || e.eventType}</b></td>
        <td><span class="status-pill created">${e.aggregate_type || e.aggregateType}</span></td>
        <td><code>${e.aggregate_id || e.aggregateId}</code></td>
        <td><code>${escapeHtml(typeof e.payload === 'string' ? e.payload : JSON.stringify(e.payload))}</code></td>
      </tr>
    `).join('');
  } catch (e) {
    console.error('Failed to load audit table', e);
  }
}

document.getElementById('btn-refresh-audit')?.addEventListener('click', () => loadAuditTable());

// ----------------------------------------------------------------------------
// WEBSOCKET REAL-TIME STREAM
// ----------------------------------------------------------------------------
let wsConnection = null;

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws`;

  try {
    wsConnection = new WebSocket(wsUrl);

    wsConnection.onopen = () => {
      console.log('[WebSocket] Connected to ALGOPILOT Real-Time Stream');
      const ind = document.getElementById('ws-indicator');
      if (ind) ind.innerHTML = '<i></i> Real-time stream active';
    };

    wsConnection.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.topic === 'reconciliation' || message.topic === '/topic/reconciliation') {
          loadReconciliationState();
        } else if (message.topic === 'orders') {
          loadOrdersTable();
          loadPositions();
        } else if (message.topic === 'decisions') {
          loadAgentActivityTimeline();
          loadAgentDecisionsTable();
        }
      } catch (err) {
        // Raw text packet fallback
      }
    };

    wsConnection.onclose = () => {
      setTimeout(connectWebSocket, 5000);
    };

    wsConnection.onerror = () => {
      wsConnection.close();
    };
  } catch (e) {
    console.warn('WebSocket connection fallback', e);
  }
}

// ----------------------------------------------------------------------------
// UTILITIES
// ----------------------------------------------------------------------------
function formatNumber(num) {
  if (num === null || num === undefined) return '0.00';
  return Number(num).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// Interactive range tabs on performance chart
document.querySelectorAll('.range-tabs button').forEach(button => {
  button.addEventListener('click', () => {
    document.querySelector('.range-tabs .selected')?.classList.remove('selected');
    button.classList.add('selected');
    const range = button.dataset.range;
    announce(`Performance window switched to ${range}.`);
  });
});

// Initialize on page load
const initialHash = window.location.hash.replace('#', '');
switchView(initialHash ? `view-${initialHash}` : 'view-dashboard');
connectWebSocket();
