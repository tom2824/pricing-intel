package io.github.tom2824.pricingintel.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests purs, sans base : normalisation des caractéristiques, clés naturelle et d'équivalence, GTIN. */
class KeysAndSchemaTest {

    private static final AttributeSchema GPU = new AttributeSchema(List.of(
            new FamilyAttribute("chipset", "Chipset", FamilyAttribute.Type.TEXT, null, null, Set.of(FamilyAttribute.Role.EQUIVALENCE)),
            new FamilyAttribute("vram_gb", "VRAM", FamilyAttribute.Type.NUMBER, "Go", null, Set.of(FamilyAttribute.Role.EQUIVALENCE)),
            new FamilyAttribute("variant", "Variante", FamilyAttribute.Type.TEXT, null, null, Set.of(FamilyAttribute.Role.IDENTITY)),
            new FamilyAttribute("tdp_w", "TDP", FamilyAttribute.Type.NUMBER, "W", null, null)));

    @Test
    void normalizesNumbersWrittenWithUnitsAndLocales() {
        Map<String, Object> normalized = GPU.normalize(Map.of("vram_gb", "12 Go", "tdp_w", "220,0W", "chipset", "  RTX  4070 SUPER "));

        assertThat((BigDecimal) normalized.get("vram_gb")).isEqualByComparingTo("12");
        assertThat((BigDecimal) normalized.get("tdp_w")).isEqualByComparingTo("220");
        assertThat(normalized.get("chipset")).isEqualTo("RTX 4070 SUPER");
        assertThat((BigDecimal) GPU.normalize(Map.of("vram_gb", 12.0)).get("vram_gb")).isEqualByComparingTo("12");
        assertThat(Keys.valueKey(new BigDecimal("220.0").stripTrailingZeros())).isEqualTo("220");
    }

    @Test
    void rejectsUnknownAttributesAndUnreadableValues() {
        assertThatThrownBy(() -> GPU.normalize(Map.of("vram", 12))).hasMessageContaining("Unknown attribute 'vram'");
        assertThatThrownBy(() -> GPU.normalize(Map.of("vram_gb", "beaucoup"))).hasMessageContaining("expects a number");
    }

    @Test
    void enumAndBooleanValuesTakeTheirCanonicalForm() {
        AttributeSchema ssd = new AttributeSchema(List.of(
                new FamilyAttribute("interface", "Interface", FamilyAttribute.Type.ENUM, null,
                        List.of("SATA", "PCIe 4.0 NVMe"), Set.of(FamilyAttribute.Role.EQUIVALENCE)),
                new FamilyAttribute("heatsink", "Dissipateur", FamilyAttribute.Type.BOOLEAN, null, null, Set.of(FamilyAttribute.Role.IDENTITY))));

        Map<String, Object> normalized = ssd.normalize(Map.of("interface", "pcie-4.0 nvme", "heatsink", "oui"));

        assertThat(normalized.get("interface")).isEqualTo("PCIe 4.0 NVMe");
        assertThat(normalized.get("heatsink")).isEqualTo(true);
        assertThatThrownBy(() -> ssd.normalize(Map.of("interface", "USB"))).hasMessageContaining("must be one of");
    }

    @Test
    void naturalKeyIgnoresCaseAccentsAndPunctuationAndUsesIdentityAttributesOnly() {
        Map<String, Object> a = GPU.normalize(Map.of("chipset", "RTX 4070 SUPER", "vram_gb", "12 Go", "variant", "Ventus 2X OC", "tdp_w", 220));
        Map<String, Object> b = GPU.normalize(Map.of("chipset", "rtx-4070-super", "vram_gb", 12, "variant", "VENTUS 2X OC"));

        String keyA = Keys.naturalKey("gpu", "MSI", "RTX 4070 SUPER 12G VENTUS 2X OC", "x", a, GPU);
        String keyB = Keys.naturalKey("gpu", "msi", "rtx-4070-super-12g-ventus-2x-oc", "y", b, GPU);

        assertThat(keyA).isEqualTo(keyB).isEqualTo("gpu|msi|rtx4070super12gventus2xoc|variant=ventus2xoc");
        assertThat(Keys.naturalKey("gpu", "MSI", null, "Sans référence", Map.of(), GPU))
                .isEqualTo("gpu|msi|sansreference|variant=");
    }

    @Test
    void equivalenceKeyGroupsBrandsOfTheSameSegmentAndIsNullWhenIncomplete() {
        Map<String, Object> msi = GPU.normalize(Map.of("chipset", "RTX 4070 SUPER", "vram_gb", "12 Go", "variant", "Ventus"));
        Map<String, Object> gigabyte = GPU.normalize(Map.of("chipset", "rtx 4070 super", "vram_gb", 12, "variant", "Windforce"));

        assertThat(Keys.equivalenceKey("gpu", msi, GPU))
                .isEqualTo(Keys.equivalenceKey("gpu", gigabyte, GPU))
                .isEqualTo("gpu|chipset=rtx4070super|vram_gb=12");
        assertThat(Keys.equivalenceKey("gpu", Map.of("chipset", "RTX 4070"), GPU)).isNull();
        assertThat(Keys.equivalenceKey("game", Map.of(), new AttributeSchema(List.of()))).isNull();
    }

    @Test
    void gtinIsNormalizedAndChecked() {
        assertThat(Gtin.normalize("4711377114363")).isEqualTo("4711377114363");
        assertThat(Gtin.normalize("0 12345 67890 5")).isEqualTo("0012345678905");
        assertThat(Gtin.normalize("1234-5670")).isEqualTo("12345670");
        assertThatThrownBy(() -> Gtin.normalize("4711377114364")).hasMessageContaining("check digit");
        assertThatThrownBy(() -> Gtin.normalize("12345")).hasMessageContaining("digits");
    }
}
