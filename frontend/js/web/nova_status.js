import { $ } from "./dom.js";

const status = $("nova-status");
let checking = false;

function formatLatency(milliseconds) {
  return milliseconds < 1000
    ? `${milliseconds} ms`
    : `${(milliseconds / 1000).toLocaleString("en-GB", { maximumFractionDigits: 1 })} s`;
}

async function updateStatus() {
  if (checking) return;
  checking = true;
  try {
    const response = await fetch("/api/nova/status", {
      cache: "no-store",
      signal: AbortSignal.timeout(7000),
    });
    if (!response.ok) throw new Error("Status not available");
    const data = await response.json();
    if (data.connection === "mock") {
      status.textContent = "Mock mode · no Nova";
      status.dataset.state = "waiting";
    } else if (data.connection === "not_configured") {
      status.textContent = "Nova: credentials missing";
      status.dataset.state = "waiting";
    } else if (data.connection === "unreachable") {
      status.textContent = "AWS not reachable";
      status.dataset.state = "error";
    } else if (data.connection === "reachable" && data.lastOutcome === "success") {
      const latency = Number.isSafeInteger(data.lastLatencyMs) && data.lastLatencyMs >= 0
        ? ` · ${formatLatency(data.lastLatencyMs)}`
        : "";
      status.textContent = `Nova last call succeeded${latency}`;
      status.dataset.state = "ok";
    } else if (data.connection === "reachable" && data.lastOutcome === "failed") {
      const latency = Number.isSafeInteger(data.lastLatencyMs) && data.lastLatencyMs >= 0
        ? ` · ${formatLatency(data.lastLatencyMs)}`
        : "";
      status.textContent = `Nova call failed${latency}`;
      status.dataset.state = "error";
    } else if (data.connection === "reachable" && data.lastOutcome === "unverified") {
      status.textContent = "AWS reachable · Nova not called yet";
      status.dataset.state = "waiting";
    } else {
      throw new Error("Unknown status");
    }
  } catch {
    status.textContent = "Nova status not available";
    status.dataset.state = "error";
  } finally {
    checking = false;
  }
}

export function initNovaStatus() {
  updateStatus();
  window.setInterval(updateStatus, 30_000);
  document.addEventListener("compliance:check-complete", updateStatus);
}

initNovaStatus();
