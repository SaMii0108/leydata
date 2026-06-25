package com.leydata.backend.seeder;

import com.leydata.backend.datacategory.infrastructure.persistence.DataCategoryRepository;
import com.leydata.backend.entity.DataCategories;
import com.leydata.backend.entity.LegalBasisCatalog;
import com.leydata.backend.legalbasis.infrastructure.persistence.LegalBasisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Siembra los catálogos base definidos por Ley 21.719.
// Estos registros son inmutables desde el negocio (isSystem=true):
// el código no permite editarlos ni desactivarlos.
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CatalogSeeder implements CommandLineRunner {

    private final DataCategoryRepository dataCategoryRepo;
    private final LegalBasisRepository legalBasisRepo;

    @Override
    public void run(String... args) {
        seedDataCategories();
        seedLegalBasisCatalog();
    }

    private void seedDataCategories() {
        // ── Categorías sensibles — Art. 16 Ley 21.719 (isSystem=true, isSensitive=true) ──
        List<Object[]> sensitive = List.of(
            new Object[]{"SALUD",     "Datos de salud",                    "Diagnósticos, historial médico, tratamientos, medicamentos y cualquier dato relacionado con la salud física o mental.", true},
            new Object[]{"BIOMETRICO","Datos biométricos",                 "Huella digital, reconocimiento facial, iris, voz y otros datos que identifican físicamente a una persona.", true},
            new Object[]{"GENETICO",  "Datos genéticos",                   "Información sobre la composición genética de una persona obtenida del análisis de muestras biológicas.", true},
            new Object[]{"VIDA_SEXUAL","Datos de vida sexual u orientación","Información sobre la vida sexual, orientación sexual o identidad de género de una persona.", true},
            new Object[]{"RELIGION",  "Convicciones religiosas o filosóficas","Creencias, prácticas religiosas, filosóficas o espirituales de una persona.", true},
            new Object[]{"POLITICO",  "Opiniones políticas",               "Afiliación partidaria, opiniones políticas o participación en organizaciones políticas.", true},
            new Object[]{"SINDICAL",  "Datos sindicales",                  "Afiliación a sindicatos, organizaciones de trabajadores u actividad sindical.", true},
            new Object[]{"RACIAL",    "Origen racial o étnico",            "Datos que revelan el origen racial o étnico de una persona.", true}
        );

        // ── Categorías no sensibles base (isSystem=true, isSensitive=false) ──
        List<Object[]> nonSensitive = List.of(
            new Object[]{"IDENTIFICACION","Datos de identificación",        "Nombre completo, RUT, fecha de nacimiento, nacionalidad y otros datos identificatorios.", false},
            new Object[]{"CONTACTO",      "Datos de contacto",             "Email, teléfono, dirección postal y otros medios de comunicación con el titular.", false},
            new Object[]{"FINANCIERO",    "Datos financieros",             "Cuentas bancarias, tarjetas, historial crediticio, transacciones y patrimonio.", false},
            new Object[]{"LABORAL",       "Datos laborales",               "Empleador, cargo, salario, historial laboral y condiciones de trabajo.", false},
            new Object[]{"UBICACION",     "Datos de ubicación",            "Geolocalización en tiempo real o historial de ubicaciones del titular.", false},
            new Object[]{"ACADEMICO",     "Datos académicos",              "Historial educacional, títulos, certificaciones y rendimiento académico.", false},
            new Object[]{"COMPORTAMIENTO","Datos de comportamiento",       "Hábitos de consumo, preferencias, navegación web y perfiles de comportamiento.", false}
        );

        seedCategories(sensitive, true);
        seedCategories(nonSensitive, false);
    }

    private void seedCategories(List<Object[]> entries, boolean isSensitive) {
        entries.forEach(entry -> {
            String code = (String) entry[0];
            if (!dataCategoryRepo.existsByCode(code)) {
                log.info("Sembrando categoría de datos: {}", code);
                dataCategoryRepo.save(DataCategories.builder()
                        .code(code)
                        .name((String) entry[1])
                        .description((String) entry[2])
                        .isSensitive(isSensitive)
                        .isSystem(true)
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        });
    }

    private void seedLegalBasisCatalog() {
        // Bases de licitud definidas por Ley 21.719
        List<Object[]> bases = List.of(
            new Object[]{"CONSENTIMIENTO",    "Consentimiento del titular",
                "El titular otorgó consentimiento libre, informado, específico e inequívoco para uno o más fines determinados. Art. 12 Ley 21.719.", true},
            new Object[]{"CONTRATO",          "Ejecución de contrato",
                "El tratamiento es necesario para ejecutar un contrato en el que el titular es parte, o para aplicar medidas precontractuales a petición del titular. Art. 13 a) Ley 21.719.", false},
            new Object[]{"OBLIGACION_LEGAL",  "Obligación legal",
                "El tratamiento es necesario para el cumplimiento de una obligación legal aplicable al responsable. Art. 13 b) Ley 21.719.", false},
            new Object[]{"INTERES_VITAL",     "Protección de intereses vitales",
                "El tratamiento es necesario para proteger intereses vitales del titular u otra persona natural. Art. 13 c) Ley 21.719.", false},
            new Object[]{"INTERES_PUBLICO",   "Interés público o autoridad pública",
                "El tratamiento es necesario para el cumplimiento de una misión de interés público o en el ejercicio de poderes públicos. Art. 13 d) Ley 21.719.", false},
            new Object[]{"INTERES_LEGITIMO",  "Interés legítimo del responsable",
                "El tratamiento es necesario para satisfacer intereses legítimos del responsable o terceros, salvo cuando prevalezcan los intereses del titular. Art. 13 e) Ley 21.719.", false}
        );

        bases.forEach(entry -> {
            String code = (String) entry[0];
            // LegalBasisCatalogRepository hereda findById/existsById pero no findByCode
            // usamos findAll + filter para no requerir query custom en el repo legacy
            boolean exists = legalBasisRepo.findByCode(code).isPresent();
            if (!exists) {
                log.info("Sembrando base de licitud: {}", code);
                LegalBasisCatalog basis = new LegalBasisCatalog();
                basis.setCode(code);
                basis.setName((String) entry[1]);
                basis.setDescription((String) entry[2]);
                basis.setConsentRequired((Boolean) entry[3]);
                basis.setIsActive(true);
                legalBasisRepo.save(basis);
            }
        });
    }
}
