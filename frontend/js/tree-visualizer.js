/**
 * Estrutura em Arvore de Busca (Trie indexada por Cor -> 7 caracteres da Placa)
 * e renderizador visual da Trie e dos Payloads JSON trocados com a API Spring Boot.
 */
class PlateTrieNode {
  constructor() {
    this.children = {};
    this.vehicleData = null;
  }
}

class ParkingTreeIndex {
  constructor() {
    this.colorRoots = {}; // Cor -> PlateTrieNode
  }

  insert(color, plate, payload) {
    const cleanColor = (color || "DESCONHECIDA").toUpperCase();
    const cleanPlate = (plate || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
    if (!this.colorRoots[cleanColor]) {
      this.colorRoots[cleanColor] = new PlateTrieNode();
    }
    let curr = this.colorRoots[cleanColor];
    for (const ch of cleanPlate) {
      if (!curr.children[ch]) curr.children[ch] = new PlateTrieNode();
      curr = curr.children[ch];
    }
    curr.vehicleData = payload;
  }

  /**
   * 3 Camadas de Resolucao na Cancela:
   * 1. EXACT_TREE_MATCH (O(7))
   * 2. FUZZY_COLOR_TREE_MATCH (Distancia de Hamming <= 1)
   * 3. EXACT_PLATE_CROSS_COLOR
   */
  search(color, plate) {
    const cleanColor = (color || "").toUpperCase();
    const cleanPlate = (plate || "").toUpperCase().replace(/[^A-Z0-9]/g, "");

    // 1. Tentativa Exata na sub-arvore da cor
    const exactInColor = this._exactSearchInRoot(this.colorRoots[cleanColor], cleanPlate);
    if (exactInColor) return { matchType: "EXACT_TREE_MATCH (O(7))", data: exactInColor };

    // 2. Busca aproximada (Distancia de Hamming <= 1) dentro da mesma cor
    const allInColor = [];
    this._collectAll(this.colorRoots[cleanColor], "", allInColor);
    for (const item of allInColor) {
      if (this._hammingDistance(item.plate, cleanPlate) <= 1) {
        return { matchType: `FUZZY_COLOR_TREE_MATCH (Placa real: ${item.plate})`, data: item.data };
      }
    }

    // 3. Fallback: Busca exata nas demais cores (iluminacao noturna)
    for (const cKey of Object.keys(this.colorRoots)) {
      const found = this._exactSearchInRoot(this.colorRoots[cKey], cleanPlate);
      if (found) return { matchType: "EXACT_PLATE_CROSS_COLOR", data: found };
    }

    return null;
  }

  _exactSearchInRoot(root, plate) {
    if (!root) return null;
    let curr = root;
    for (const ch of plate) {
      if (!curr.children[ch]) return null;
      curr = curr.children[ch];
    }
    return curr.vehicleData;
  }

  _collectAll(node, prefix, result) {
    if (!node) return;
    if (node.vehicleData) {
      result.push({ plate: prefix, data: node.vehicleData });
    }
    for (const ch of Object.keys(node.children)) {
      this._collectAll(node.children[ch], prefix + ch, result);
    }
  }

  _hammingDistance(a, b) {
    if (a.length !== b.length) return 99;
    let dist = 0;
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) dist++;
    }
    return dist;
  }
}

function renderTreeStructureToBox(treeIndex, lot, elementId = "treeVisualizerBox") {
  const box = document.getElementById(elementId);
  if (!box || !lot) return;

  const lines = [`[ROOT: ${lot.name}]`];
  const colors = Object.keys(treeIndex.colorRoots);
  if (colors.length === 0) {
    lines.push("  └── (Nenhuma placa indexada no pátio no momento)");
  } else {
    colors.forEach(color => {
      lines.push(`  ├── 🎨 COR: ${color}`);
      const list = [];
      treeIndex._collectAll(treeIndex.colorRoots[color], "", list);
      list.forEach(item => {
        const charsChain = item.plate.split("").join(" ➔ ");
        lines.push(`  │    └── [${charsChain}] ➔ Vaga ${item.data.spotCode} (${item.data.status} | ${item.data.driver})`);
      });
    });
  }
  box.textContent = lines.join("\n");
}

function renderJsonPayloadToBox(payloadObj, elementId = "jsonPayloadBox") {
  const box = document.getElementById(elementId);
  if (!box) return;
  box.textContent = JSON.stringify(payloadObj, null, 2);
}

window.PlateTrieNode = PlateTrieNode;
window.ParkingTreeIndex = ParkingTreeIndex;
window.renderTreeStructureToBox = renderTreeStructureToBox;
window.renderJsonPayloadToBox = renderJsonPayloadToBox;
