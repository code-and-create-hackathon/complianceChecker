import { $ } from "./dom.js";

const status = $("nova-status");
let checking = false;

function formatLatency(milliseconds) {
  return milliseconds < 1000
    ? `${milliseconds} ms`
    : `${(milliseconds / 1000).toLocaleString("de-DE", { maximumFractionDigits: 1 })} s`;
}

async function updateStatus() {
  if (checking) return;
  checking = true;
  try {
    const response = await fetch("/api/nova/status", {
      cache: "no-store",
      signal: AbortSignal.timeout(7000),
    });
    if (!response.ok) throw new Error("Status nicht verfügbar");
    const data = await response.json();
    if (data.connection === "not_configured") {
      status.textContent = "Nova: Zugangsdaten fehlen";
      status.dataset.state = "waiting";
    } else if (data.connection === "unreachable") {
      status.textContent = "AWS nicht erreichbar";
      status.dataset.state = "error";
    } else if (data.connection === "reachable" && data.lastOutcome === "success") {
      const latency = Number.isSafeInteger(data.lastLatencyMs) && data.lastLatencyMs >= 0
        ? ` · ${formatLatency(data.lastLatencyMs)}`
        : "";
      status.textContent = `Nova zuletzt erfolgreich${latency}`;
      status.dataset.state = "ok";
    } else if (data.connection === "reachable" && data.lastOutcome === "failed") {
      const latency = Number.isSafeInteger(data.lastLatencyMs) && data.lastLatencyMs >= 0
        ? ` · ${formatLatency(data.lastLatencyMs)}`
        : "";
      status.textContent = `Nova-Aufruf fehlgeschlagen${latency}`;
      status.dataset.state = "error";
    } else if (data.connection === "reachable" && data.lastOutcome === "unverified") {
      status.textContent = "AWS erreichbar · Nova noch nicht aufgerufen";
      status.dataset.state = "waiting";
    } else {
      throw new Error("Unbekannter Status");
    }
  } catch {
    status.textContent = "Nova-Status nicht verfügbar";
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
