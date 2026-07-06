package com.example.vizi.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TemplateServiceTests {

    private final TemplateRepository repository = mock(TemplateRepository.class);
    private final TemplateService service = new TemplateService(repository);

    @Test
    void listActiveTemplatesMapsEntitiesToPublicListItems() {
        when(repository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(template("Basic Card")));

        var templates = service.listActiveTemplates();

        assertThat(templates).hasSize(1);
        assertThat(templates.getFirst().name()).isEqualTo("Basic Card");
        assertThat(templates.getFirst().widthMm()).isEqualByComparingTo("90.00");
    }

    @Test
    void getActiveTemplateReturnsDetailOr404() {
        when(repository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(template("Basic Card")));
        when(repository.findByIdAndActiveTrue(404L)).thenReturn(Optional.empty());

        assertThat(service.getActiveTemplate(10L).canvasJson()).contains("\"layers\"");
        assertThatThrownBy(() -> service.getActiveTemplate(404L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Template not found");
                });
    }

    private static Template template(String name) {
        return new Template(
                name,
                "business",
                "https://cdn.example.test/basic.png",
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[]}",
                true
        );
    }
}
