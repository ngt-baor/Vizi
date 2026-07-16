package com.example.vizi.design;

import java.math.BigDecimal;
import java.util.Optional;

import com.example.vizi.auth.AuthService;
import com.example.vizi.auth.User;
import com.example.vizi.preflight.PreflightService;
import com.example.vizi.template.TemplateService;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignServiceV2Tests {

    private final DesignRepository designRepository = mock(DesignRepository.class);
    private final DesignSnapshotRepository snapshotRepository = mock(DesignSnapshotRepository.class);
    private final TemplateService templateService = mock(TemplateService.class);
    private final AuthService authService = mock(AuthService.class);
    private final PreflightService preflightService = mock(PreflightService.class);
    private final DesignService service = new DesignService(
            designRepository,
            snapshotRepository,
            templateService,
            authService,
            new ObjectMapper(),
            preflightService
    );

    @Test
    void ownerCanSaveV2FrontAndBackDocument() {
        var user = new User("owner@example.test", "test-hash", "Owner");
        var design = new Design(
                user,
                "Original",
                "{\"layers\":[]}",
                new BigDecimal("90.00"),
                new BigDecimal("54.00")
        );
        String canvasJson = """
                {
                  "schemaVersion": 2,
                  "documentId": "42",
                  "name": "V2 Card",
                  "card": {"widthMm": 90, "heightMm": 54},
                  "pages": {
                    "front": {"id": "front", "name": "Front", "background": "#ffffff", "layers": [
                      {"id": "front-title", "name": "Title", "type": "text"}
                    ]},
                    "back": {"id": "back", "name": "Back", "background": "#111111", "layers": []}
                  },
                  "updatedAt": "2026-07-17T00:00:00Z"
                }
                """;

        when(authService.requireUser("owner@example.test")).thenReturn(user);
        when(designRepository.findByIdAndUser_Id(42L, user.id())).thenReturn(Optional.of(design));

        var saved = service.updateOwnedDesign(
                42L,
                new SaveDesignRequest("V2 Card", canvasJson),
                "owner@example.test"
        );

        assertThat(saved.name()).isEqualTo("V2 Card");
        assertThat(saved.canvasJson()).isEqualTo(canvasJson);
        verify(snapshotRepository).save(any(DesignSnapshot.class));
    }

    @Test
    void v2DocumentWithExtraPageIsRejected() {
        var user = new User("owner@example.test", "test-hash", "Owner");
        var design = new Design(
                user,
                "Original",
                "{\"layers\":[]}",
                new BigDecimal("90.00"),
                new BigDecimal("54.00")
        );
        String canvasJson = """
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"id": "front", "layers": []},
                    "back": {"id": "back", "layers": []},
                    "extra": {"id": "extra", "layers": []}
                  }
                }
                """;

        when(authService.requireUser("owner@example.test")).thenReturn(user);
        when(designRepository.findByIdAndUser_Id(42L, user.id())).thenReturn(Optional.of(design));

        assertThatThrownBy(() -> service.updateOwnedDesign(
                42L,
                new SaveDesignRequest("V2 Card", canvasJson),
                "owner@example.test"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("front/back pages");
    }
    @Test
    void v2DocumentWithoutPagesObjectIsRejected() {
        var user = new User("owner@example.test", "test-hash", "Owner");
        var design = new Design(
                user,
                "Original",
                "{\"layers\":[]}",
                new BigDecimal("90.00"),
                new BigDecimal("54.00")
        );
        String canvasJson = """
                {
                  "schemaVersion": 2,
                  "documentId": "42"
                }
                """;

        when(authService.requireUser("owner@example.test")).thenReturn(user);
        when(designRepository.findByIdAndUser_Id(42L, user.id())).thenReturn(Optional.of(design));

        assertThatThrownBy(() -> service.updateOwnedDesign(
                42L,
                new SaveDesignRequest("V2 Card", canvasJson),
                "owner@example.test"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("front/back pages");
    }
}
