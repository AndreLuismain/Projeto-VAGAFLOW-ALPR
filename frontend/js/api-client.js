/**
 * Cliente HTTP REST para integracao com o backend Java 21 + Spring Boot 3
 * (http://localhost:8080/api/v1).
 * Possui deteccao automatica de conectividade: quando o backend Spring Boot esta ativo,
 * todas as operacoes ocorrem via API REST; caso contrario, opera em modo fallback local.
 */
const API_BASE_URL = "http://localhost:8080/api/v1";

class VagaFlowApiClient {
  constructor() {
    this.backendOnline = false;
  }

  async checkBackendHealth() {
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 1200);
      const res = await fetch(`${API_BASE_URL}/parking-lots`, {
        method: "GET",
        signal: controller.signal
      });
      clearTimeout(timeout);
      this.backendOnline = res.ok;
      return this.backendOnline;
    } catch (_) {
      this.backendOnline = false;
      return false;
    }
  }

  async listParkingLots() {
    const res = await fetch(`${API_BASE_URL}/parking-lots`);
    if (!res.ok) throw new Error("Falha ao listar estacionamentos na API");
    return res.json();
  }

  async createParkingLot(payload) {
    const res = await fetch(`${API_BASE_URL}/parking-lots`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || "Falha ao cadastrar estacionamento");
    }
    return res.json();
  }

  async getMetrics() {
    const res = await fetch(`${API_BASE_URL}/parking-lots/metrics`);
    if (!res.ok) throw new Error("Falha ao consultar metricas financeiras");
    return res.json();
  }

  async createReservationSyncGate(payload) {
    const res = await fetch(`${API_BASE_URL}/reservations/sync-gate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || "Falha ao criar reserva na API");
    }
    return res.json();
  }

  async triggerNoShow(reservationId) {
    const res = await fetch(`${API_BASE_URL}/reservations/${encodeURIComponent(reservationId)}/trigger-no-show`, {
      method: "POST"
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || "Falha ao disparar expiracao de No-Show");
    }
    return res.json();
  }

  async processGateEvent(payload) {
    const res = await fetch(`${API_BASE_URL}/gate/events`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || "Falha ao processar evento da cancela ALPR");
    }
    return res.json();
  }

  async testTreeSearch(lotId, color, plate) {
    const params = new URLSearchParams({ lotId, color, plate });
    const res = await fetch(`${API_BASE_URL}/gate/search?${params.toString()}`);
    if (!res.ok) throw new Error("Falha ao consultar Arvore Trie na API");
    return res.json();
  }
}

window.vagaFlowApi = new VagaFlowApiClient();
