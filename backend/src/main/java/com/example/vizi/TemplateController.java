package com.example.vizi;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
class TemplateController {

    private final TemplateService templateService;

    TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    List<TemplateListItem> listTemplates() {
        return templateService.listActiveTemplates();
    }

    @GetMapping("/{id}")
    TemplateDetail getTemplate(@PathVariable Long id) {
        return templateService.getActiveTemplate(id);
    }
}
