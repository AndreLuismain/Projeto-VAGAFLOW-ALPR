package com.vagaflow.alpr.engine;

import com.vagaflow.alpr.domain.enums.SpotStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * No da Arvore de Prefixos (Trie) para indexacao dos 7 caracteres alfanumericos de placas Mercosul/antigas.
 * Utiliza ConcurrentSkipListMap para manter as transicoes de caracteres ordenadas e thread-safe.
 */
public class PlateTrieNode {

    private final Map<Character, PlateTrieNode> children = new ConcurrentSkipListMap<>();
    private volatile IndexedVehiclePayload vehicleData;

    public Map<Character, PlateTrieNode> getChildren() {
        return children;
    }

    public IndexedVehiclePayload getVehicleData() {
        return vehicleData;
    }

    public void setVehicleData(IndexedVehiclePayload vehicleData) {
        this.vehicleData = vehicleData;
    }

    /**
     * Metadados do veiculo armazenados no no-folha (7o nivel da Trie).
     */
    public record IndexedVehiclePayload(
            String spotCode,
            SpotStatus status,
            String driver,
            String plate,
            String color
    ) {
    }
}
