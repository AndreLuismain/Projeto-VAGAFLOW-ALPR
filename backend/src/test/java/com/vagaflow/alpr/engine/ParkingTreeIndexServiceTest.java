package com.vagaflow.alpr.engine;

import com.vagaflow.alpr.domain.enums.SpotStatus;
import com.vagaflow.alpr.engine.ParkingTreeIndexService.TreeMatchResult;
import com.vagaflow.alpr.engine.PlateTrieNode.IndexedVehiclePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de testes unitarios para a Arvore de Busca ALPR (Cor -> Trie de Prefixos de 7 caracteres).
 */
class ParkingTreeIndexServiceTest {

    private ParkingTreeIndexService treeIndexService;

    @BeforeEach
    void setUp() {
        treeIndexService = new ParkingTreeIndexService();
        treeIndexService.insert(
                "LOT-01",
                "PRATA",
                "BRA2E19",
                new IndexedVehiclePayload("A-03", SpotStatus.RESERVED, "André Luís", "BRA2E19", "PRATA")
        );
        treeIndexService.insert(
                "LOT-01",
                "PRETO",
                "QWE9A88",
                new IndexedVehiclePayload("A-01", SpotStatus.OCCUPIED, "Avulso Local", "QWE9A88", "PRETO")
        );
    }

    @Test
    @DisplayName("Camada 1: Deve realizar busca exata O(7) na sub-arvore da cor detectada")
    void shouldMatchExactPlateInSameColorRoot() {
        Optional<TreeMatchResult> result = treeIndexService.search("LOT-01", "PRATA", "BRA2E19");

        assertTrue(result.isPresent());
        assertEquals("EXACT_TREE_MATCH (O(7))", result.get().matchType());
        assertEquals("A-03", result.get().data().spotCode());
        assertEquals(SpotStatus.RESERVED, result.get().data().status());
        assertEquals("André Luís", result.get().data().driver());
    }

    @Test
    @DisplayName("Camada 2: Deve recuperar placa real via Distancia de Hamming <= 1 quando OCR errar 1 caractere na mesma cor")
    void shouldRecoverPlateWithFuzzyHammingDistanceOneInSameColor() {
        // Simula reflexo optico onde '9' foi lido como '8' ('BRA2E18' em vez de 'BRA2E19')
        Optional<TreeMatchResult> result = treeIndexService.search("LOT-01", "PRATA", "BRA2E18");

        assertTrue(result.isPresent());
        assertEquals("FUZZY_COLOR_TREE_MATCH (Placa real: BRA2E19)", result.get().matchType());
        assertEquals("BRA2E19", result.get().data().plate());
        assertEquals("A-03", result.get().data().spotCode());
    }

    @Test
    @DisplayName("Camada 3: Deve localizar placa exata em outra cor (Cross-Color Fallback) em caso de variacao de iluminacao noturna")
    void shouldMatchExactPlateAcrossColorsWhenLightingChangesColorPerception() {
        // Veiculo cadastrado como PRATA, mas camera noturna detectou PRETO com placa exata BRA2E19
        Optional<TreeMatchResult> result = treeIndexService.search("LOT-01", "PRETO", "BRA2E19");

        assertTrue(result.isPresent());
        assertEquals("EXACT_PLATE_CROSS_COLOR", result.get().matchType());
        assertEquals("A-03", result.get().data().spotCode());
    }

    @Test
    @DisplayName("Nao deve retornar match quando a distancia de Hamming for maior ou igual a 2 caracteres")
    void shouldReturnEmptyWhenHammingDistanceIsTwoOrMore() {
        Optional<TreeMatchResult> result = treeIndexService.search("LOT-01", "PRATA", "BRA0X00");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Deve remover veiculo da Arvore e gerar visualizacao hierarquica formatada")
    void shouldRemoveVehicleAndDumpFormattedTree() {
        List<String> linesBefore = treeIndexService.dumpTreeLines("LOT-01", "Estacionamento Centro Rápido");
        assertTrue(linesBefore.stream().anyMatch(l -> l.contains("B -> R -> A -> 2 -> E -> 1 -> 9")));

        treeIndexService.remove("LOT-01", "PRATA", "BRA2E19");
        Optional<TreeMatchResult> afterRemove = treeIndexService.search("LOT-01", "PRATA", "BRA2E19");
        assertTrue(afterRemove.isEmpty());
    }
}
