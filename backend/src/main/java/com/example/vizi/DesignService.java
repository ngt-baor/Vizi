package com.example.vizi;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
class DesignService {

    private final DesignRepository designRepository;
    private final TemplateRepository templateRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    DesignService(
            DesignRepository designRepository,
            TemplateRepository templateRepository,
            AuthService authService,
            ObjectMapper objectMapper
    ) {
        this.designRepository = designRepository;
        this.templateRepository = templateRepository;
        this.authService = authService;
        this.objectMapper = objectMapper;
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
        var template = templateRepository.findByIdAndActiveTrue(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
        return DesignDetail.from(designRepository.save(new Design(user, template)));
    }

    DesignDetail getOwnedDesign(Long designId, String email) {
        var user = authService.requireUser(email);
        return designRepository.findByIdAndUser_Id(designId, user.id())
                .map(DesignDetail::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
    }

    @Transactional
    DesignDetail updateOwnedDesign(Long designId, SaveDesignRequest request, String email) {
        var user = authService.requireUser(email);
        var design = designRepository.findByIdAndUser_Id(designId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        validateCanvas(request.canvasJson());
        design.update(request.name().trim(), request.canvasJson());
        return DesignDetail.from(design);
    }

    @Transactional
    void deleteOwnedDesign(Long designId, String email) {
        var user = authService.requireUser(email);
        var design = designRepository.findByIdAndUser_Id(designId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        designRepository.delete(design);
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
