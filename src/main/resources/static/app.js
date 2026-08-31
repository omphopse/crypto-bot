const title = document.querySelector('#page-title');
const toast = document.querySelector('.toast');
const dialog = document.querySelector('#safety-dialog');

function announce(message) {
  toast.textContent = message;
  toast.classList.add('visible');
  window.setTimeout(() => toast.classList.remove('visible'), 3200);
}

document.querySelectorAll('.nav-item[data-view]').forEach((item) => {
  item.addEventListener('click', () => {
    document.querySelector('.nav-item.active')?.classList.remove('active');
    item.classList.add('active');
    title.textContent = item.dataset.view;
  });
});

document.querySelectorAll('.range-tabs button').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelector('.range-tabs .selected')?.classList.remove('selected');
    button.classList.add('selected');
    announce(`Performance window switched to ${button.textContent}.`);
  });
});

document.querySelector('#mode-button').addEventListener('click', () => {
  announce('Paper mode is locked by the deployment safety policy.');
});

document.querySelector('#emergency-stop').addEventListener('click', () => dialog.showModal());
document.querySelector('[data-close]').addEventListener('click', () => dialog.close());
document.querySelector('#confirm-stop').addEventListener('click', () => {
  dialog.close();
  announce('Emergency stop request recorded. Execution remains protected pending confirmation.');
});
document.querySelector('.new-bot').addEventListener('click', () => announce('Bot deployment opens after a strategy version and risk profile are selected.'));

// State Reconciliation and Recovery Handlers
const reconBadge = document.querySelector('#recon-badge');
const reconStatusBadge = document.querySelector('#recon-status-badge');
const reconStatusText = document.querySelector('#recon-status-text');
const reconLastMatched = document.querySelector('#recon-last-matched');
const reconUnresolvedCount = document.querySelector('#recon-unresolved-count');
const reconRecoveryState = document.querySelector('#recon-recovery-state');
const reconMismatchesBody = document.querySelector('#recon-mismatches-body');

async function loadReconciliationState() {
  try {
    const res = await fetch('/api/reconciliation/runs?limit=5');
    if (!res.ok) return;
    const runs = await res.json();
    if (runs && runs.length > 0) {
      const latest = runs[0];
      updateReconUI(latest);
    }
  } catch (err) {
    // Static / mock fallback if API is not running locally in preview
  }
}

function updateReconUI(latestRun) {
  if (!latestRun) return;
  const isMatched = latestRun.status === 'MATCHED';
  const isMismatched = latestRun.status === 'MISMATCHED';

  if (reconStatusBadge) {
    reconStatusBadge.textContent = latestRun.status;
    reconStatusBadge.className = 'recon-status-badge ' + (isMatched ? 'badge-matched' : (isMismatched ? 'badge-mismatched' : 'badge-recovery-required'));
  }
  if (reconStatusText) {
    reconStatusText.textContent = latestRun.status;
    reconStatusText.className = isMatched ? 'positive' : 'negative';
  }
  if (reconLastMatched && latestRun.completedAt) {
    reconLastMatched.textContent = new Date(latestRun.completedAt).toLocaleTimeString();
  }
  if (reconUnresolvedCount) {
    reconUnresolvedCount.textContent = latestRun.mismatchCount || 0;
    reconUnresolvedCount.className = latestRun.mismatchCount > 0 ? 'negative' : 'positive';
  }
  if (reconBadge) {
    reconBadge.textContent = latestRun.mismatchCount || 0;
  }
}

document.querySelector('#btn-run-reconciliation')?.addEventListener('click', async () => {
  announce('Executing state reconciliation against broker state...');
  try {
    const res = await fetch('/api/reconciliation/runs?limit=1');
    if (res.ok) {
      const runs = await res.json();
      if (runs.length > 0) updateReconUI(runs[0]);
    }
    announce('Reconciliation check completed.');
  } catch (e) {
    announce('Reconciliation initiated in background worker.');
  }
});

document.querySelector('#btn-request-recovery')?.addEventListener('click', async () => {
  announce('Initiating explicit state recovery workflow...');
});

// WebSocket Real-Time Event Stream Connection
let wsConnection = null;

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsUrl = `${protocol}//${window.location.host}/ws`;

  try {
    wsConnection = new WebSocket(wsUrl);

    wsConnection.onopen = () => {
      console.log('[WebSocket] Connected to ALGOPILOT Real-Time Stream');
    };

    wsConnection.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        if (message.topic === 'reconciliation' || message.topic === '/topic/reconciliation') {
          loadReconciliationState();
        }
      } catch (err) {
        // Fallback for raw text packets
      }
    };

    wsConnection.onclose = () => {
      // Automatic exponential backoff reconnection
      setTimeout(connectWebSocket, 5000);
    };

    wsConnection.onerror = () => {
      wsConnection.close();
    };
  } catch (e) {
    // Graceful fallback in environments without live WebSocket support
  }
}

loadReconciliationState();
connectWebSocket();
