package com.example.vizi.design;

import java.util.List;

import jakarta.validation.Valid;

import com.example.vizi.preflight.PreflightReport;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/designs")
class DesignController {

    private final DesignService designService;

    DesignController(DesignService designService) {
        this.designService = designService;
    }

    @GetMapping
    List<DesignListItem> listDesigns(Authentication authentication) {
        return designService.listOwnedDesigns(authentication.getName());
    }

    @PostMapping("/from-template/{templateId}")
    @ResponseStatus(HttpStatus.CREATED)
    DesignDetail createFromTemplate(@PathVariable Long templateId, Authentication authentication) {
        return designService.createFromTemplate(templateId, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DesignDetail createBlank(
            @Valid @RequestBody CreateDesignRequest request,
            Authentication authentication
    ) {
        return designService.createBlank(request, authentication.getName());
    }

    @GetMapping("/{designId}")
    DesignDetail getDesign(@PathVariable Long designId, Authentication authentication) {
        return designService.getOwnedDesign(designId, authentication.getName());
    }

    @PutMapping("/{designId}")
    DesignDetail updateDesign(
            @PathVariable Long designId,
            @Valid @RequestBody SaveDesignRequest request,
            Authentication authentication
    ) {
        return designService.updateOwnedDesign(designId, request, authentication.getName());
    }

    @DeleteMapping("/{designId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDesign(@PathVariable Long designId, Authentication authentication) {
        designService.deleteOwnedDesign(designId, authentication.getName());
    }

    @PostMapping("/{designId}/preflight")
    PreflightReport runPreflight(@PathVariable Long designId, Authentication authentication) {
        return designService.runPreflight(designId, authentication.getName());
    }
}
