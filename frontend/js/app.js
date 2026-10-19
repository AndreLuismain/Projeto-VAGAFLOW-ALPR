/**
 * Controlador principal do Workspace Integrado VagaFlow ALPR:
 * - Coluna 1: App do Motorista (B2C) + Pop-up obrigatorio dos 15 minutos
 * - Coluna 2: Simulador de Camera ALPR + Arvore de Busca (Cor -> Trie 7 chars) + JSON Sync
 * - Coluna 3: Portal Web do Estacionamento (B2B) + Bento Grid + Split Financeiro
 *
 * Opera conectado em tempo real com a API Spring Boot (http://localhost:8080/api/v1)
 * e mantem fallback local automatico caso o servidor backend nao esteja em execucao.
 */
const PLATFORM_FEE = 1.00; // R$ 1,00 fixo sempre para a plataforma
let photoAngle = "TRASEIRA";
let platformRevenueTotal = 0.0;
let partnerRevenueTotal = 0.0;
let totalOperations = 0;

let parkingLots = [
  {
    id: "LOT-01",
    name: "Estacionamento Centro Rápido",
    distance: "180m (2 quadras)",
    ownerHourlyRate: 7.00,
    spots: [
      { code: "A-01", status: "OCCUPIED", plate: "QWE9A88", color: "PRETO", driver: "Avulso Local" },
      { code: "A-02", status: "OCCUPIED", plate: "ERT4B11", color: "BRANCO", driver: "Avulso Local" },
      { code: "A-03", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "A-04", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "A-05", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "A-06", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "A-07", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "A-08", status: "AVAILABLE", plate: null, color: null, driver: null }
    ]
  },
  {
    id: "LOT-02",
    name: "Farmácia & Comércio Central",
    distance: "210m (2 quadras)",
    ownerHourlyRate: 5.00,
    spots: [
      { code: "F-01", status: "OCCUPIED", plate: "MNB7C22", color: "PRATA", driver: "Cliente Farmácia" },
      { code: "F-02", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "F-03", status: "AVAILABLE", plate: null, color: null, driver: null },
      { code: "F-04", status: "AVAILABLE", plate: null, color: null, driver: null }
    ]
  }
];

let selectedLotId = "LOT-01";
let pendingLotIdForModal = null;
let activeReservation = null;
let countdownInterval = null;
let gateTreeIndex = new ParkingTreeIndex();

function getSelectedLot() {
  return parkingLots.find(l => l.id === selectedLotId) || parkingLots[0];
}

function rebuildTreeIndex() {
  gateTreeIndex = new ParkingTreeIndex();
  const lot = getSelectedLot();
  if (!lot) return;
  lot.spots.forEach(spot => {
    if (spot.status !== "AVAILABLE" && spot.plate) {
      gateTreeIndex.insert(spot.color, spot.plate, {
        spotCode: spot.code,
        status: spot.status,
        driver: spot.driver,
        plate: spot.plate,
        color: spot.color
      });
    }
  });
  renderTreeStructureToBox(gateTreeIndex, lot);
}

async function syncFromSpringBackendIfOnline() {
  const isOnline = await window.vagaFlowApi.checkBackendHealth();
  const badge = document.getElementById("backendConnectionBadge");
  if (isOnline) {
    if (badge) {
      badge.textContent = "API SPRING BOOT: ONLINE (:8080)";
      badge.className = "tag tag-green";
    }
    try {
      const remoteLots = await window.vagaFlowApi.listParkingLots();
      parkingLots = remoteLots.map(l => ({
        id: l.id,
        name: l.name,
        distance: l.distance,
        ownerHourlyRate: Number(l.ownerHourlyRate),
        spots: l.spots
      }));
      const metrics = await window.vagaFlowApi.getMetrics();
      platformRevenueTotal = Number(metrics.platformRevenueTotal);
      partnerRevenueTotal = Number(metrics.partnerRevenueTotal);
      totalOperations = Number(metrics.totalOperations);
    } catch (e) {
      console.warn("Erro ao sincronizar estado inicial da API:", e);
    }
  } else if (badge) {
    badge.textContent = "MODO SIMULADOR LOCAL (FALLBACK)";
    badge.className = "tag tag-amber";
  }
}

function syncDriverPlatePreview() {
  const plate = document.getElementById("driverPlate").value.toUpperCase() || "AAA0A00";
  const color = document.getElementById("driverColor").value;
  document.getElementById("platePreviewText").textContent = plate;
  document.getElementById("colorPreviewText").textContent = color;
  document.getElementById("cameraPlateInput").value = plate;
  document.getElementById("cameraColorInput").value = color;
}

function togglePhotoAngle() {
  photoAngle = photoAngle === "TRASEIRA" ? "FRONTAL" : "TRASEIRA";
  document.getElementById("photoAngleText").textContent =
    `Foto ${photoAngle === "TRASEIRA" ? "Traseira" : "Frontal"} Anexada`;
}

function renderLotsList() {
  const container = document.getElementById("lotsListContainer");
  container.innerHTML = "";

  parkingLots.forEach(lot => {
    const freeSpots = lot.spots.filter(s => s.status === "AVAILABLE").length;
    const totalHourlyPrice = lot.ownerHourlyRate + PLATFORM_FEE;
    const noShowTotal = totalHourlyPrice / 2;
    const isSelected = lot.id === selectedLotId;

    const card = document.createElement("div");
    card.className = `lot-card ${isSelected ? "selected" : ""}`;
    card.onclick = () => {
      selectedLotId = lot.id;
      renderAll();
    };

    let statusTag = `<span class="tag tag-green">${freeSpots} VAGAS LIVRES</span>`;
    if (freeSpots === 0) statusTag = `<span class="tag tag-red">LOTADO</span>`;
    else if (freeSpots <= 2) statusTag = `<span class="tag tag-amber">ÚLTIMAS ${freeSpots} VAGAS</span>`;

    card.innerHTML = `
      <div class="lot-card-top">
        <div>
          <strong style="font-size: 0.95rem;">${lot.name}</strong>
          <div style="font-size: 0.76rem; color: var(--text-muted);">📍 ${lot.distance} • Multa 15m: R$ ${noShowTotal.toFixed(2)}</div>
        </div>
        <div class="lot-price-tag">R$ ${totalHourlyPrice.toFixed(2)}/h</div>
      </div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px;">
        ${statusTag}
        <button class="btn btn-amber" style="padding: 7px 12px; font-size: 0.8rem;"
          ${freeSpots === 0 || activeReservation ? "disabled style='opacity:0.45; cursor:not-allowed; padding: 7px 12px; font-size: 0.8rem;'" : ""}
          onclick="event.stopPropagation(); openReserveModal('${lot.id}')">
          Reservar Vaga (15m)
        </button>
      </div>
    `;
    container.appendChild(card);
  });
}

// Abre o Pop-up com a mensagem obrigatoria antes de confirmar reserva
function openReserveModal(lotId) {
  pendingLotIdForModal = lotId;
  const lot = parkingLots.find(l => l.id === lotId);
  const totalMinPrice = lot.ownerHourlyRate + PLATFORM_FEE;
  const penaltyTotal = totalMinPrice / 2;
  const penaltyOwner = penaltyTotal - PLATFORM_FEE;

  const breakdown = document.getElementById("modalPriceBreakdown");
  breakdown.innerHTML = `
    <div class="split-row"><span>Estacionamento selecionado:</span><strong>${lot.name}</strong></div>
    <div class="split-row"><span>Valor Mínimo (1ª Hora no App):</span><strong>R$ ${totalMinPrice.toFixed(2)} (R$ ${lot.ownerHourlyRate.toFixed(2)} pátio + R$ 1,00 app)</strong></div>
    <hr style="border: none; border-top: 1px solid #262A33; margin: 4px 0;" />
    <div class="split-row" style="color: var(--signal-yellow);"><span>Cobrança se não chegar em 15 min (50%):</span><strong>R$ ${penaltyTotal.toFixed(2)}</strong></div>
    <div class="split-row" style="color: var(--sensor-green);"><span>↳ Repasse ao Dono do Estacionamento:</span><strong>R$ ${penaltyOwner.toFixed(2)}</strong></div>
    <div class="split-row" style="color: var(--signal-amber);"><span>↳ Sua Taxa Fixa da Plataforma:</span><strong>R$ ${PLATFORM_FEE.toFixed(2)}</strong></div>
  `;

  document.getElementById("reserveModal").classList.add("active");
}

function closeReserveModal() {
  pendingLotIdForModal = null;
  document.getElementById("reserveModal").classList.remove("active");
}

async function confirmReservation() {
  const lot = parkingLots.find(l => l.id === pendingLotIdForModal);
  closeReserveModal();
  if (!lot) return;

  const driverName = document.getElementById("driverName").value.trim() || "Motorista App";
  const plate = document.getElementById("driverPlate").value.toUpperCase().trim() || "BRA2E19";
  const color = document.getElementById("driverColor").value;

  if (window.vagaFlowApi.backendOnline) {
    try {
      const syncPayload = await window.vagaFlowApi.createReservationSyncGate({
        lotId: lot.id,
        driverName,
        licensePlate: plate,
        vehicleColor: color,
        photoAngle
      });

      selectedLotId = lot.id;
      activeReservation = {
        reservationId: syncPayload.reservationId,
        lotId: lot.id,
        lotName: lot.name,
        spotCode: syncPayload.parkingLotSync.allocatedSpotCode,
        driverName,
        plate,
        color,
        photoAngle,
        ownerHourlyRate: lot.ownerHourlyRate,
        totalHourlyRate: lot.ownerHourlyRate + PLATFORM_FEE,
        remainingSeconds: 15 * 60,
        status: "RESERVED"
      };

      await syncFromSpringBackendIfOnline();
      renderJsonPayloadToBox(syncPayload);
      logGateEvent(`🔒 [API Spring] Reserva ${activeReservation.reservationId}: Vaga ${activeReservation.spotCode} travada por 15 min para placa ${plate} (${color}).`);
      startCountdownTimer();
      renderAll();
      return;
    } catch (err) {
      alert(err.message);
      return;
    }
  }

  // Modo Local Fallback
  const freeSpot = lot.spots.find(s => s.status === "AVAILABLE");
  if (!freeSpot) return alert("Sem vagas disponíveis!");

  selectedLotId = lot.id;
  freeSpot.status = "RESERVED";
  freeSpot.plate = plate;
  freeSpot.color = color;
  freeSpot.driver = driverName;

  const now = new Date();
  const expires = new Date(now.getTime() + 15 * 60 * 1000);

  activeReservation = {
    reservationId: "RES-" + Math.floor(100000 + Math.random() * 900000),
    lotId: lot.id,
    lotName: lot.name,
    spotCode: freeSpot.code,
    driverName,
    plate,
    color,
    photoAngle,
    ownerHourlyRate: lot.ownerHourlyRate,
    totalHourlyRate: lot.ownerHourlyRate + PLATFORM_FEE,
    remainingSeconds: 15 * 60,
    status: "RESERVED"
  };

  const syncPayload = {
    event: "RESERVATION_CREATED_LOCK_SPOT",
    reservationId: activeReservation.reservationId,
    timestamp: now.toISOString(),
    expiresAt: expires.toISOString(),
    ttlMinutes: 15,
    customerVehicle: {
      driverName: driverName,
      licensePlate: plate,
      vehicleColor: color,
      platePhotoReference: {
        position: photoAngle,
        url: `https://cdn.vagaflow.app/plates/${plate}_${photoAngle.toLowerCase()}.jpg`,
        ocrPreValidated: true
      }
    },
    parkingLotSync: {
      lotId: lot.id,
      allocatedSpotCode: freeSpot.code,
      temporaryLockStatus: "RESERVED",
      indexedInColorPrefixTree: true,
      pricingRule: {
        minimumHourlyTotalBRL: activeReservation.totalHourlyRate,
        ownerBaseShareBRL: lot.ownerHourlyRate,
        platformFixedFeeBRL: PLATFORM_FEE,
        noShow15MinPenaltyTotalBRL: activeReservation.totalHourlyRate / 2,
        noShowOwnerShareBRL: (activeReservation.totalHourlyRate / 2) - PLATFORM_FEE,
        noShowPlatformShareBRL: PLATFORM_FEE
      }
    }
  };

  renderJsonPayloadToBox(syncPayload);
  logGateEvent(`🔒 Reserva ${activeReservation.reservationId}: Vaga ${freeSpot.code} travada por 15 min para placa ${plate} (${color}). JSON sincronizado na Árvore da Câmera.`);

  startCountdownTimer();
  renderAll();
}

function startCountdownTimer() {
  if (countdownInterval) clearInterval(countdownInterval);
  countdownInterval = setInterval(() => {
    if (!activeReservation || activeReservation.status !== "RESERVED") {
      clearInterval(countdownInterval);
      return;
    }
    activeReservation.remainingSeconds--;
    if (activeReservation.remainingSeconds <= 0) {
      triggerNoShowPenalty();
    } else {
      renderActiveReservationBox();
    }
  }, 1000);
}

async function triggerNoShowPenalty() {
  if (!activeReservation) return;
  clearInterval(countdownInterval);

  if (window.vagaFlowApi.backendOnline) {
    try {
      const noShowJson = await window.vagaFlowApi.triggerNoShow(activeReservation.reservationId);
      await syncFromSpringBackendIfOnline();
      renderJsonPayloadToBox(noShowJson);
      const charge = Number(noShowJson.automaticSplitCharge.chargedFromCustomerBRL);
      const ownerShare = Number(noShowJson.automaticSplitCharge.transferredToParkingOwnerBRL);
      const platformShare = Number(noShowJson.automaticSplitCharge.transferredToPlatformBRL);
      logGateEvent(`⏰ NO-SHOW (15 min expirados) na vaga ${noShowJson.spotReleased} (${noShowJson.licensePlate})! Cobrado R$ ${charge.toFixed(2)} ➔ R$ ${ownerShare.toFixed(2)} p/ Dono + R$ ${platformShare.toFixed(2)} p/ Você.`);
      activeReservation = null;
      renderAll();
      return;
    } catch (err) {
      console.warn("Fallback local no-show:", err);
    }
  }

  const lot = parkingLots.find(l => l.id === activeReservation.lotId);
  const spot = lot.spots.find(s => s.code === activeReservation.spotCode);

  const totalPenalty = activeReservation.totalHourlyRate / 2;
  const ownerShare = totalPenalty - PLATFORM_FEE;
  const platformShare = PLATFORM_FEE;

  platformRevenueTotal += platformShare;
  partnerRevenueTotal += ownerShare;
  totalOperations += 1;

  if (spot) {
    spot.status = "AVAILABLE";
    spot.plate = null;
    spot.color = null;
    spot.driver = null;
  }

  const noShowJson = {
    event: "RESERVATION_EXPIRED_NO_SHOW_15MIN",
    reservationId: activeReservation.reservationId,
    licensePlate: activeReservation.plate,
    spotReleased: activeReservation.spotCode,
    automaticSplitCharge: {
      chargedFromCustomerBRL: totalPenalty,
      transferredToParkingOwnerBRL: ownerShare,
      transferredToPlatformBRL: platformShare,
      ruleApplied: "50% do valor mínimo de 1h (R$ 1,00 fixo da plataforma preservado)"
    }
  };

  renderJsonPayloadToBox(noShowJson);
  logGateEvent(`⏰ NO-SHOW (15 min expirados) na vaga ${activeReservation.spotCode} (${activeReservation.plate})! Cobrado R$ ${totalPenalty.toFixed(2)} ➔ R$ ${ownerShare.toFixed(2)} p/ Dono + R$ ${platformShare.toFixed(2)} p/ Você.`);

  activeReservation = null;
  renderAll();
}

function renderActiveReservationBox() {
  const box = document.getElementById("activeReservationContainer");
  if (!box) return;
  if (!activeReservation) {
    box.style.display = "none";
    box.innerHTML = "";
    return;
  }

  box.style.display = "block";
  if (activeReservation.status === "RESERVED") {
    const mins = String(Math.floor(activeReservation.remainingSeconds / 60)).padStart(2, "0");
    const secs = String(activeReservation.remainingSeconds % 60).padStart(2, "0");
    const penalty = (activeReservation.totalHourlyRate / 2).toFixed(2);

    box.innerHTML = `
      <div class="reservation-box">
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span class="tag tag-amber">VAGA ${activeReservation.spotCode} RESERVADA</span>
          <span class="mono" style="font-size: 0.75rem;">${activeReservation.lotName}</span>
        </div>
        <div class="timer-display">${mins}:${secs}</div>
        <p style="font-size: 0.78rem; color: var(--text-muted); text-align: center;">
          Passe pela câmera de entrada antes do timer zerar para não pagar a taxa de no-show (R$ ${penalty}).
        </p>
        <button class="btn btn-red" style="width: 100%; font-size: 0.8rem;" onclick="triggerNoShowPenalty()">
          ⏩ Simular Atraso de 15 Minutos (Testar Multa 50% + Split)
        </button>
      </div>
    `;
  } else if (activeReservation.status === "ACTIVE") {
    box.innerHTML = `
      <div class="reservation-box" style="border-color: var(--sensor-green); background: rgba(16, 185, 129, 0.08);">
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span class="tag tag-green">ESTACIONADO NA VAGA ${activeReservation.spotCode}</span>
          <span class="mono" style="font-size: 0.75rem;">CANCELA VALIDADA</span>
        </div>
        <p style="font-size: 0.84rem;">
          Veículo <strong>${activeReservation.plate} (${activeReservation.color})</strong> reconhecido pela câmera via busca em árvore!
        </p>
        <button class="btn btn-amber" style="width: 100%; font-size: 0.82rem;" onclick="simulateCameraEvent('EXIT')">
          🏁 Sair do Estacionamento (Cobrar 1h = R$ ${activeReservation.totalHourlyRate.toFixed(2)})
        </button>
      </div>
    `;
  }
}

async function simulateCameraEvent(direction) {
  const lot = getSelectedLot();
  const plateInput = document.getElementById("cameraPlateInput").value.toUpperCase().trim();
  const colorInput = document.getElementById("cameraColorInput").value;
  const gateBadge = document.getElementById("gateStatusBadge");

  if (window.vagaFlowApi.backendOnline) {
    try {
      const apiResp = await window.vagaFlowApi.processGateEvent({
        lotId: lot.id,
        direction,
        detectedPlate: plateInput,
        detectedColor: colorInput,
        driverName: document.getElementById("driverName").value.trim() || "Motorista App"
      });

      await syncFromSpringBackendIfOnline();
      renderJsonPayloadToBox(apiResp);

      if (direction === "ENTRY") {
        if (activeReservation && apiResp.spotActivated === activeReservation.spotCode) {
          activeReservation.status = "ACTIVE";
        }
        gateBadge.textContent = apiResp.event === "ALPR_GATE_ENTRY_MATCHED"
          ? "CANCELA ABERTA (RESERVA OK)"
          : "CANCELA ABERTA (AVULSO/DIRETO)";
        gateBadge.className = "tag tag-green";
        logGateEvent(`🟢 ${apiResp.message}`);
      } else {
        if (activeReservation && apiResp.releasedSpot === activeReservation.spotCode) {
          activeReservation = null;
        }
        gateBadge.textContent = "SAÍDA LIBERADA + PAGO";
        gateBadge.className = "tag tag-amber";
        logGateEvent(`🏁 ${apiResp.message}`);
      }
      renderAll();
      return;
    } catch (err) {
      alert(err.message);
      return;
    }
  }

  // Modo Local Fallback
  rebuildTreeIndex();
  const treeMatch = gateTreeIndex.search(colorInput, plateInput);

  if (direction === "ENTRY") {
    if (treeMatch && treeMatch.data.status === "RESERVED") {
      const spot = lot.spots.find(s => s.code === treeMatch.data.spotCode);
      spot.status = "OCCUPIED";
      if (activeReservation && activeReservation.spotCode === spot.code) {
        activeReservation.status = "ACTIVE";
      }

      gateBadge.textContent = "CANCELA ABERTA (RESERVA OK)";
      gateBadge.className = "tag tag-green";

      const entryJson = {
        event: "ALPR_GATE_ENTRY_MATCHED",
        treeLookupStrategy: treeMatch.matchType,
        detectedPlate: plateInput,
        detectedColor: colorInput,
        spotActivated: spot.code,
        gateCommand: "OPEN_BARRIER_IMMEDIATELY"
      };
      renderJsonPayloadToBox(entryJson);
      logGateEvent(`🟢 ENTRADA LIBERADA [${treeMatch.matchType}]: Placa ${plateInput} (${colorInput}) ocupou a vaga reservada ${spot.code}.`);
    } else {
      const freeSpot = lot.spots.find(s => s.status === "AVAILABLE");
      if (!freeSpot) {
        alert("Estacionamento lotado!");
        return;
      }
      freeSpot.status = "OCCUPIED";
      freeSpot.plate = plateInput;
      freeSpot.color = colorInput;
      freeSpot.driver = "Motorista App Direto";

      gateBadge.textContent = "CANCELA ABERTA (AVULSO/DIRETO)";
      gateBadge.className = "tag tag-green";
      logGateEvent(`🟢 ENTRADA DIRETA: Placa ${plateInput} (${colorInput}) alocada na vaga ${freeSpot.code} e indexada na Árvore.`);
    }
  } else if (direction === "EXIT") {
    if (!treeMatch) {
      alert("Veículo não encontrado na Árvore de Busca deste pátio! Verifique a placa ou simule a entrada primeiro.");
      return;
    }
    const spot = lot.spots.find(s => s.code === treeMatch.data.spotCode);
    const totalAmount = lot.ownerHourlyRate + PLATFORM_FEE;
    const ownerShare = lot.ownerHourlyRate;

    platformRevenueTotal += PLATFORM_FEE;
    partnerRevenueTotal += ownerShare;
    totalOperations += 1;

    spot.status = "AVAILABLE";
    spot.plate = null;
    spot.color = null;
    spot.driver = null;

    if (activeReservation && activeReservation.spotCode === spot.code) {
      activeReservation = null;
    }

    gateBadge.textContent = "SAÍDA LIBERADA + PAGO";
    gateBadge.className = "tag tag-amber";

    const exitJson = {
      event: "ALPR_GATE_EXIT_CHECKOUT",
      treeLookupStrategy: treeMatch.matchType,
      licensePlate: plateInput,
      releasedSpot: spot.code,
      billingSplit: {
        totalChargedBRL: totalAmount,
        parkingOwnerShareBRL: ownerShare,
        platformFixedFeeBRL: PLATFORM_FEE
      }
    };
    renderJsonPayloadToBox(exitJson);
    logGateEvent(`🏁 SAÍDA & SPLIT: Placa ${plateInput} liberou a vaga ${spot.code}. Cobrado R$ ${totalAmount.toFixed(2)} (R$ ${ownerShare.toFixed(2)} Dono + R$ 1,00 Seu).`);
  }

  renderAll();
}

function simulateAvulsoCar() {
  const randomPlates = ["KJH8F44", "PZX3D90", "LMN2C77", "VBN5A12"];
  const randomColors = ["PRETO", "BRANCO", "PRATA", "VERMELHO"];
  document.getElementById("cameraPlateInput").value = randomPlates[Math.floor(Math.random() * randomPlates.length)];
  document.getElementById("cameraColorInput").value = randomColors[Math.floor(Math.random() * randomColors.length)];
  simulateCameraEvent("ENTRY");
}

async function simulateFuzzyOcrMatch() {
  const realPlate = document.getElementById("driverPlate").value.toUpperCase().trim() || "BRA2E19";
  const corruptedPlate = realPlate.slice(0, -1) + (realPlate.endsWith("9") ? "8" : "9");
  const color = document.getElementById("driverColor").value;

  document.getElementById("cameraPlateInput").value = corruptedPlate;
  document.getElementById("cameraColorInput").value = color;

  rebuildTreeIndex();
  const result = gateTreeIndex.search(color, corruptedPlate);
  if (result) {
    logGateEvent(`🔍 TESTE ÁRVORE OCR: Câmera leu '${corruptedPlate}' (1 char errado), mas a sub-árvore da cor '${color}' recuperou '${realPlate}' na vaga ${result.data.spotCode}!`);
  } else {
    logGateEvent(`ℹ️ Reserve ou estacione o carro '${realPlate}' primeiro para testar a recuperação por Árvore de Cor + Placa.`);
  }
}

async function createNewParkingLot() {
  const name = document.getElementById("newLotName").value.trim() || "Estacionamento Novo";
  const ownerRate = parseFloat(document.getElementById("newLotRate").value) || 7.0;
  const count = Math.min(16, Math.max(2, parseInt(document.getElementById("newLotSpots").value) || 8));

  if (window.vagaFlowApi.backendOnline) {
    try {
      const created = await window.vagaFlowApi.createParkingLot({
        name,
        ownerHourlyRate: ownerRate,
        spotsCount: count,
        distance: "300m"
      });
      await syncFromSpringBackendIfOnline();
      selectedLotId = created.id;
      document.getElementById("newLotName").value = "";
      logGateEvent(`🏢 [API Spring] Novo estacionamento '${created.name}' cadastrado! Valor do dono: R$ ${ownerRate.toFixed(2)}/h | Valor no App: R$ ${(ownerRate + 1).toFixed(2)}/h.`);
      renderAll();
      return;
    } catch (err) {
      alert(err.message);
      return;
    }
  }

  const spots = [];
  for (let i = 1; i <= count; i++) {
    spots.push({
      code: `V-${String(i).padStart(2, "0")}`,
      status: "AVAILABLE",
      plate: null,
      color: null,
      driver: null
    });
  }

  const newLot = {
    id: "LOT-0" + (parkingLots.length + 1),
    name,
    distance: "300m",
    ownerHourlyRate: ownerRate,
    spots
  };

  parkingLots.push(newLot);
  selectedLotId = newLot.id;
  document.getElementById("newLotName").value = "";
  logGateEvent(`🏢 Novo estacionamento '${name}' cadastrado! Valor do dono: R$ ${ownerRate.toFixed(2)}/h | Valor no App: R$ ${(ownerRate + 1).toFixed(2)}/h.`);
  renderAll();
}

function renderB2BDashboard() {
  const lot = getSelectedLot();
  if (!lot) return;
  document.getElementById("b2bLotTitle").textContent =
    `Pátio: ${lot.name} (Dono: R$ ${lot.ownerHourlyRate.toFixed(2)}/h | App: R$ ${(lot.ownerHourlyRate + 1).toFixed(2)}/h)`;

  const freeCount = lot.spots.filter(s => s.status === "AVAILABLE").length;
  const resCount = lot.spots.filter(s => s.status === "RESERVED").length;
  const occCount = lot.spots.filter(s => s.status === "OCCUPIED").length;
  document.getElementById("b2bOccupancySummary").textContent =
    `${freeCount} Livres • ${resCount} Reservadas • ${occCount} Ocupadas`;

  const grid = document.getElementById("b2bSpotsGrid");
  grid.innerHTML = "";
  lot.spots.forEach(spot => {
    const cell = document.createElement("div");
    cell.className = `spot-cell ${spot.status}`;
    cell.innerHTML = `
      <div class="spot-code">${spot.code}</div>
      <div class="spot-status">${spot.status === "AVAILABLE" ? "LIVRE" : spot.plate}</div>
      <div style="font-size: 0.65rem; color: var(--signal-amber);">${spot.color || "-"}</div>
    `;
    grid.appendChild(cell);
  });
}

function logGateEvent(msg) {
  const feed = document.getElementById("gateFeedList");
  if (!feed) return;
  const item = document.createElement("div");
  item.className = "feed-item";
  const timeStr = new Date().toLocaleTimeString("pt-BR");
  item.innerHTML = `<span>${msg}</span><span class="mono" style="color: var(--text-muted); font-size: 0.7rem;">${timeStr}</span>`;
  feed.prepend(item);
}

function renderMetrics() {
  document.getElementById("platformRevenue").textContent = `R$ ${platformRevenueTotal.toFixed(2)}`;
  document.getElementById("partnerRevenue").textContent = `R$ ${partnerRevenueTotal.toFixed(2)}`;
  document.getElementById("totalOps").textContent = String(totalOperations);
}

function renderAll() {
  renderLotsList();
  renderActiveReservationBox();
  renderB2BDashboard();
  rebuildTreeIndex();
  renderMetrics();
}

window.onload = async () => {
  syncDriverPlatePreview();
  await syncFromSpringBackendIfOnline();
  renderJsonPayloadToBox({
    status: "IDLE_WAITING_RESERVATION",
    info: "Clique em 'Reservar Vaga (15m)' no App do Motorista à esquerda para disparar o pop-up de aviso e sincronizar o JSON com a câmera."
  });
  logGateEvent("🚀 Sistema iniciado. Câmera ALPR e Árvore de Busca prontas.");
  renderAll();
};
