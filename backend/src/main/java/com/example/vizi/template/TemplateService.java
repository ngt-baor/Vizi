package com.example.vizi.template;

import java.math.BigDecimal;
import java.util.List;

import com.example.vizi.auth.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final AuthService authService;

    public TemplateService(TemplateRepository templateRepository, AuthService authService) {
        this.templateRepository = templateRepository;
        this.authService = authService;
    }

    List<TemplateListItem> listActiveTemplates() {
        return templateRepository.findByActiveTrueOrderByIdAsc()
                .stream()
                .map(TemplateListItem::from)
                .toList();
    }

    TemplateDetail getActiveTemplate(Long id) {
        return TemplateDetail.from(requireActiveTemplate(id));
    }

    public Template requireActiveTemplate(Long id) {
        return templateRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    }

    public List<AdminTemplateItem> listAllTemplatesForAdmin(String adminEmail) {
        authService.requireAdmin(adminEmail);
        return templateRepository.findAllByOrderByIdAsc().stream().map(AdminTemplateItem::from).toList();
    }

    @Transactional
    public AdminTemplateItem createTemplateForAdmin(String adminEmail, AdminTemplateRequest request) {
        authService.requireAdmin(adminEmail);
        validateSize(request.widthMm(), request.heightMm());
        var template = templateRepository.save(new Template(
                request.name().trim(),
                request.category().trim(),
                blankToNull(request.previewUrl()),
                request.widthMm(),
                request.heightMm(),
                request.canvasJson(),
                request.active()
        ));
        return AdminTemplateItem.from(template);
    }

    @Transactional
    public AdminTemplateItem updateTemplateForAdmin(Long id, String adminEmail, AdminTemplateRequest request) {
        authService.requireAdmin(adminEmail);
        validateSize(request.widthMm(), request.heightMm());
        var template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        template.update(
                request.name().trim(),
                request.category().trim(),
                blankToNull(request.previewUrl()),
                request.widthMm(),
                request.heightMm(),
                request.canvasJson(),
                request.active()
        );
        return AdminTemplateItem.from(templateRepository.save(template));
    }

    @Transactional
    public AdminTemplateItem setTemplateActiveForAdmin(Long id, boolean active, String adminEmail) {
        authService.requireAdmin(adminEmail);
        var template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        template.setActive(active);
        return AdminTemplateItem.from(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplateForAdmin(Long id, String adminEmail) {
        authService.requireAdmin(adminEmail);
        if (!templateRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found");
        }
        // Soft-delete: unpublish instead of hard delete to protect design FKs.
        var template = templateRepository.findById(id).orElseThrow();
        template.setActive(false);
        templateRepository.save(template);
    }

    private static void validateSize(BigDecimal widthMm, BigDecimal heightMm) {
        if (widthMm == null || heightMm == null || widthMm.signum() <= 0 || heightMm.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card size must be positive");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
