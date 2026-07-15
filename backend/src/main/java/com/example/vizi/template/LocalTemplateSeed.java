package com.example.vizi.template;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds demo templates when missing so /templates is usable without admin publish.
 * Idempotent: only inserts if name does not already exist.
 */
@Component
@Profile("local")
class LocalTemplateSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalTemplateSeed.class);
    private static final String GEO_RED_NAME = "Geo Red Corporate Card";

    private final TemplateRepository templateRepository;

    LocalTemplateSeed(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasGeoRed = templateRepository.findAllByOrderByIdAsc().stream()
                .anyMatch(t -> GEO_RED_NAME.equalsIgnoreCase(t.name()));
        if (hasGeoRed) {
            return;
        }

        String canvasJson = """
                {
                  "layers": [
                    {
                      "id": "front-art",
                      "type": "image",
                      "name": "Front art",
                      "page": "front",
                      "src": "/cards/geo-red-front.svg",
                      "x": 0,
                      "y": 0,
                      "width": 100,
                      "height": 100,
                      "opacity": 1,
                      "stroke": "transparent",
                      "strokeWidth": 0,
                      "radius": 0,
                      "fill": "transparent"
                    },
                    {
                      "id": "back-art",
                      "type": "image",
                      "name": "Back art",
                      "page": "back",
                      "src": "/cards/geo-red-back.svg",
                      "x": 0,
                      "y": 0,
                      "width": 100,
                      "height": 100,
                      "opacity": 1,
                      "stroke": "transparent",
                      "strokeWidth": 0,
                      "radius": 0,
                      "fill": "transparent"
                    }
                  ]
                }
                """.replaceAll("\\s+", " ").trim();

        templateRepository.save(new Template(
                GEO_RED_NAME,
                "Corporate",
                "/cards/geo-red-front.svg",
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                canvasJson,
                true
        ));

        // Blank layout so users can start from empty canvas too.
        boolean hasBlank = templateRepository.findAllByOrderByIdAsc().stream()
                .anyMatch(t -> "Blank 90×54".equalsIgnoreCase(t.name()) || "Blank 90x54".equalsIgnoreCase(t.name()));
        if (!hasBlank) {
            templateRepository.save(new Template(
                    "Blank 90x54",
                    "Basic",
                    null,
                    new BigDecimal("90.00"),
                    new BigDecimal("54.00"),
                    "{\"layers\":[]}",
                    true
            ));
        }

        log.info("Local template seed: created '{}'", GEO_RED_NAME);
    }
}
