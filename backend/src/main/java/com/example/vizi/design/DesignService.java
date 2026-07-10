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

    DesignDetail getOwnedDesign(Long designId, String email) {
        var user = authService.requireUser(email);
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
            var layers = canvas.get("layers");
            if (!canvas.isObject() || layers == null || !layers.isArray() || layers.size() > 500) {
                throw new IllegalArgumentException("Canvas must contain at most 500 layers");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Canvas JSON is invalid", exception);
        }
    }
}
