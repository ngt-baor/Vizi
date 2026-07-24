package com.example.vizi.design;

import java.util.List;

import com.example.vizi.auth.AuthService;
import com.example.vizi.preflight.PreflightReport;
import com.example.vizi.preflight.PreflightService;
import com.example.vizi.template.TemplateService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class DesignService {

    private final DesignRepository designRepository;
    private final DesignSnapshotRepository designSnapshotRepository;
    private final TemplateService templateService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final PreflightService preflightService;

    DesignService(
            DesignRepository designRepository,
            DesignSnapshotRepository designSnapshotRepository,
            TemplateService templateService,
            AuthService authService,
            ObjectMapper objectMapper,
            PreflightService preflightService
    ) {
        this.designRepository = designRepository;
        this.designSnapshotRepository = designSnapshotRepository;
        this.templateService = templateService;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.preflightService = preflightService;
    }

    List<DesignListItem> listOwnedDesigns(String email) {
        var user = authService.requireUser(email);
        return designRepository.findByUser_IdOrderByUpdatedAtDesc(user.id())
                .stream()
                .map(DesignListItem::from)
                .toList();
    }

    @Transactional
    DesignDetail createFromTemplate(Long templateId, String email) {
        var user = authService.requireUser(email);
        var template = templateService.requireActiveTemplate(templateId);
        return DesignDetail.from(designRepository.save(new Design(user, template)));
    }

    @Transactional
    DesignDetail createBlank(CreateDesignRequest request, String email) {
        try {
            var user = authService.requireUser(email);
            String canvas = request.canvasJson() == null || request.canvasJson().isBlank()
                    ? "{\"layers\":[]}"
                    : request.canvasJson().trim();
            validateCanvas(canvas);
            var design = new Design(
                    user,
                    request.name().trim(),
                    canvas,
                    request.widthMm(),
                    request.heightMm()
            );
            return DesignDetail.from(designRepository.save(design));
        } catch (ResponseStatusException | IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error",
                    exception
            );
        }
    }

    DesignDetail getOwnedDesign(Long designId, String email) {
        var user = authService.requireUser(email);
        // Admin may view any design (album / publish-to-template flow).
        if (user.isAdmin()) {
            return designRepository.findById(designId)
                    .map(DesignDetail::from)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        }
        return designRepository.findByIdAndUser_Id(designId, user.id())
                .map(DesignDetail::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
    }

    public String getOwnedCanvas(Long designId, String email) {
        return getOwnedDesign(designId, email).canvasJson();
    }

    @Transactional
    DesignDetail updateOwnedDesign(Long designId, SaveDesignRequest request, String email) {
        var user = authService.requireUser(email);
        var design = designRepository.findByIdAndUser_Id(designId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        validateCanvas(request.canvasJson());
        design.update(request.name().trim(), request.canvasJson());
        designSnapshotRepository.save(new DesignSnapshot(design, user, "save", request.canvasJson()));
        return DesignDetail.from(design);
    }

    @Transactional
    void deleteOwnedDesign(Long designId, String email) {
        var user = authService.requireUser(email);
        var design = designRepository.findByIdAndUser_Id(designId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        designRepository.delete(design);
    }

    PreflightReport runPreflight(Long designId, String email) {
        var user = authService.requireUser(email);
        var design = designRepository.findByIdAndUser_Id(designId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        return preflightService.check(
                design.canvasJson(),
                design.widthMm().doubleValue(),
                design.heightMm().doubleValue()
        );
    }

    private void validateCanvas(String canvasJson) {
        try {
            var canvas = objectMapper.readTree(canvasJson);
            if (!canvas.isObject()) {
                throw new IllegalArgumentException("Canvas JSON must be an object");
            }

            var legacyLayers = canvas.get("layers");
            if (legacyLayers != null) {
                if (!legacyLayers.isArray() || legacyLayers.size() > 500) {
                    throw new IllegalArgumentException("Canvas must contain at most 500 layers");
                }
                return;
            }

            var schemaVersion = canvas.get("schemaVersion");
            var pages = canvas.get("pages");
            var front = pages == null ? null : pages.get("front");
            var back = pages == null ? null : pages.get("back");
            var frontLayers = front == null ? null : front.get("layers");
            var backLayers = back == null ? null : back.get("layers");
            boolean validV2 = schemaVersion != null
                    && schemaVersion.isIntegralNumber() && schemaVersion.intValue() == 2
                    && pages != null
                    && pages.isObject()
                    && pages.size() == 2
                    && front != null
                    && front.isObject()
                    && front.get("id") != null && front.get("id").isString() && "front".equals(front.get("id").stringValue())
                    && back != null
                    && back.isObject()
                    && back.get("id") != null && back.get("id").isString() && "back".equals(back.get("id").stringValue())
                    && frontLayers != null
                    && frontLayers.isArray()
                    && backLayers != null
                    && backLayers.isArray();
            if (!validV2) {
                throw new IllegalArgumentException("Canvas must contain a layers array or V2 front/back pages");
            }
            if (frontLayers.size() + backLayers.size() > 500) {
                throw new IllegalArgumentException("Canvas must contain at most 500 layers");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Canvas JSON is invalid", exception);
        }
    }
}
