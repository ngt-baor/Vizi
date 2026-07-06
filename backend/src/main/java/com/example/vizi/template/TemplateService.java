package com.example.vizi.template;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateRepository templateRepository;

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
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
}
