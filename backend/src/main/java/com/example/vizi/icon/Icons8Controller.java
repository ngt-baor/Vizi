package com.example.vizi.icon;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class Icons8Controller {

    private final Icons8Service icons8Service;

    Icons8Controller(Icons8Service icons8Service) {
        this.icons8Service = icons8Service;
    }

    @GetMapping("/api/icons8/search")
    Icons8SearchResponse search(
            @RequestParam String term,
            @RequestParam(defaultValue = "en") String language,
            @RequestParam(defaultValue = "") String platform,
            @RequestParam(defaultValue = "48") int amount,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return icons8Service.search(term, language, platform, amount, offset);
    }
}
