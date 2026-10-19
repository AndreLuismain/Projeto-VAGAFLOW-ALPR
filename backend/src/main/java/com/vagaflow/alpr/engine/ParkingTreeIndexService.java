package com.vagaflow.alpr.engine;

import com.vagaflow.alpr.domain.entity.ParkingLot;
import com.vagaflow.alpr.domain.entity.Spot;
import com.vagaflow.alpr.domain.enums.SpotStatus;
import com.vagaflow.alpr.engine.PlateTrieNode.IndexedVehiclePayload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de Indexacao e Busca em Arvore para Cameras ALPR (Automatic License Plate Recognition).
 *
 * Estrutura Hierarquica por Patio (lotId):
 *   Nivel 0: Raiz do Estacionamento
 *   Nivel 1: Particionamento por Cor do Veiculo (PRATA, PRETO, BRANCO, VERMELHO, AZUL)
 *   Niveis 2 a 8: Arvore de Prefixos (Trie de 7 niveis) para cada caractere da placa (L = 7)
 *
 * Camadas de Resolucao na Cancela (search):
 *   1. EXACT_TREE_MATCH (O(7)) — Busca exata nos 7 niveis dentro da sub-arvore da cor detectada.
 *   2. FUZZY_COLOR_TREE_MATCH — Distancia de Hamming <= 1 dentro da sub-arvore da mesma cor
 *      (recupera falhas opticas de 1 caractere causadas por reflexo, chuva ou sujeira na lente).
 *   3. EXACT_PLATE_CROSS_COLOR — Fallback que busca a placa exata nas demais cores caso
 *      a iluminacao noturna altere a percepcao de cor da camera.
 */
@Service
public class ParkingTreeIndexService {

    /**
     * Mapa de lotId -> (Mapa de Cor -> PlateTrieNode raiz daquela cor).
     */
    private final Map<String, Map<String, PlateTrieNode>> lotColorTrees = new ConcurrentHashMap<>();

    public record TreeMatchResult(
            String matchType,
            IndexedVehiclePayload data
    ) {
    }

    private record LeafEntry(
            String plate,
            IndexedVehiclePayload data
    ) {
    }

    /**
     * Reconstrói integralmente o indice em arvore de um patio a partir do estado atual de suas vagas.
     */
    public synchronized void rebuildLotIndex(ParkingLot lot) {
        Map<String, PlateTrieNode> colorRoots = new LinkedHashMap<>();
        for (Spot spot : lot.getSpots()) {
            if (spot.getStatus() != SpotStatus.AVAILABLE && spot.getPlate() != null && !spot.getPlate().isBlank()) {
                insertIntoColorMap(
                        colorRoots,
                        spot.getColor(),
                        spot.getPlate(),
                        new IndexedVehiclePayload(
                                spot.getCode(),
                                spot.getStatus(),
                                spot.getDriver() != null ? spot.getDriver() : "Motorista",
                                normalizePlate(spot.getPlate()),
                                normalizeColor(spot.getColor())
                        )
                );
            }
        }
        lotColorTrees.put(lot.getId(), colorRoots);
    }

    /**
     * Insere diretamente um veiculo na Arvore de Prefixos do patio especificado.
     */
    public synchronized void insert(String lotId, String color, String plate, IndexedVehiclePayload payload) {
        Map<String, PlateTrieNode> colorRoots = lotColorTrees.computeIfAbsent(lotId, k -> new LinkedHashMap<>());
        insertIntoColorMap(colorRoots, color, plate, payload);
    }

    /**
     * Remove um veiculo da Arvore de Prefixos do patio especificado.
     */
    public synchronized void remove(String lotId, String color, String plate) {
        Map<String, PlateTrieNode> colorRoots = lotColorTrees.get(lotId);
        if (colorRoots == null) {
            return;
        }
        String cleanColor = normalizeColor(color);
        String cleanPlate = normalizePlate(plate);
        PlateTrieNode root = colorRoots.get(cleanColor);
        if (root == null) {
            return;
        }
        PlateTrieNode curr = root;
        for (char ch : cleanPlate.toCharArray()) {
            curr = curr.getChildren().get(ch);
            if (curr == null) {
                return;
            }
        }
        curr.setVehicleData(null);
    }

    /**
     * Executa a busca em 3 camadas na Arvore do patio.
     */
    public synchronized Optional<TreeMatchResult> search(String lotId, String color, String plate) {
        Map<String, PlateTrieNode> colorRoots = lotColorTrees.get(lotId);
        if (colorRoots == null || colorRoots.isEmpty()) {
            return Optional.empty();
        }

        String cleanColor = normalizeColor(color);
        String cleanPlate = normalizePlate(plate);

        // 1. Tentativa Exata O(7) na sub-arvore da cor detectada
        PlateTrieNode sameColorRoot = colorRoots.get(cleanColor);
        IndexedVehiclePayload exactInColor = exactSearchInRoot(sameColorRoot, cleanPlate);
        if (exactInColor != null) {
            return Optional.of(new TreeMatchResult("EXACT_TREE_MATCH (O(7))", exactInColor));
        }

        // 2. Busca aproximada (Distancia de Hamming <= 1) dentro da mesma cor
        if (sameColorRoot != null) {
            List<LeafEntry> allInColor = new ArrayList<>();
            collectAll(sameColorRoot, "", allInColor);
            for (LeafEntry item : allInColor) {
                if (hammingDistance(item.plate(), cleanPlate) <= 1) {
                    return Optional.of(new TreeMatchResult(
                            "FUZZY_COLOR_TREE_MATCH (Placa real: " + item.plate() + ")",
                            item.data()
                    ));
                }
            }
        }

        // 3. Fallback: Busca exata nas demais cores (iluminacao noturna / sombra)
        for (Map.Entry<String, PlateTrieNode> entry : colorRoots.entrySet()) {
            if (!entry.getKey().equals(cleanColor)) {
                IndexedVehiclePayload foundCrossColor = exactSearchInRoot(entry.getValue(), cleanPlate);
                if (foundCrossColor != null) {
                    return Optional.of(new TreeMatchResult("EXACT_PLATE_CROSS_COLOR", foundCrossColor));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Gera a representacao textual hierarquica da Arvore para o visualizador em tempo real.
     */
    public synchronized List<String> dumpTreeLines(String lotId, String lotName) {
        List<String> lines = new ArrayList<>();
        lines.add("[ROOT: " + lotName + "]");
        Map<String, PlateTrieNode> colorRoots = lotColorTrees.get(lotId);
        if (colorRoots == null || colorRoots.isEmpty()) {
            lines.add("  └── (Nenhuma placa indexada no pátio no momento)");
            return lines;
        }

        boolean hasAnyVehicle = false;
        for (Map.Entry<String, PlateTrieNode> entry : colorRoots.entrySet()) {
            List<LeafEntry> leaves = new ArrayList<>();
            collectAll(entry.getValue(), "", leaves);
            if (!leaves.isEmpty()) {
                hasAnyVehicle = true;
                lines.add("  ├── COR: " + entry.getKey());
                for (LeafEntry item : leaves) {
                    String charsChain = String.join(" -> ", item.plate().split(""));
                    lines.add(String.format("  │    └── [%s] -> Vaga %s (%s | %s)",
                            charsChain,
                            item.data().spotCode(),
                            item.data().status(),
                            item.data().driver()));
                }
            }
        }

        if (!hasAnyVehicle) {
            lines.add("  └── (Nenhuma placa indexada no pátio no momento)");
        }
        return lines;
    }

    public int hammingDistance(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return 99;
        }
        int dist = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                dist++;
            }
        }
        return dist;
    }

    private void insertIntoColorMap(
            Map<String, PlateTrieNode> colorRoots,
            String color,
            String plate,
            IndexedVehiclePayload payload) {
        String cleanColor = normalizeColor(color);
        String cleanPlate = normalizePlate(plate);
        if (cleanPlate.isEmpty()) {
            return;
        }
        PlateTrieNode curr = colorRoots.computeIfAbsent(cleanColor, k -> new PlateTrieNode());
        for (char ch : cleanPlate.toCharArray()) {
            curr = curr.getChildren().computeIfAbsent(ch, k -> new PlateTrieNode());
        }
        curr.setVehicleData(payload);
    }

    private IndexedVehiclePayload exactSearchInRoot(PlateTrieNode root, String plate) {
        if (root == null || plate == null || plate.isEmpty()) {
            return null;
        }
        PlateTrieNode curr = root;
        for (char ch : plate.toCharArray()) {
            curr = curr.getChildren().get(ch);
            if (curr == null) {
                return null;
            }
        }
        return curr.getVehicleData();
    }

    private void collectAll(PlateTrieNode node, String prefix, List<LeafEntry> result) {
        if (node == null) {
            return;
        }
        if (node.getVehicleData() != null) {
            result.add(new LeafEntry(prefix, node.getVehicleData()));
        }
        for (Map.Entry<Character, PlateTrieNode> child : node.getChildren().entrySet()) {
            collectAll(child.getValue(), prefix + child.getKey(), result);
        }
    }

    private String normalizeColor(String color) {
        return (color == null || color.isBlank()) ? "DESCONHECIDA" : color.trim().toUpperCase();
    }

    private String normalizePlate(String plate) {
        return (plate == null) ? "" : plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
