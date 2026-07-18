package com.example.vizi.paper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.example.vizi.auth.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PaperCatalogService {

    private final PaperStockRepository paperStockRepository;
    private final AuthService authService;

    PaperCatalogService(PaperStockRepository paperStockRepository, AuthService authService) {
        this.paperStockRepository = paperStockRepository;
        this.authService = authService;
    }

    public List<PaperStockResponse> listPublic() {
        return paperStockRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(PaperStockResponse::from)
                .toList();
    }

    public List<PaperStockResponse> listForAdmin(String adminEmail) {
        authService.requireAdmin(adminEmail);
        return paperStockRepository.findAllByOrderByIdAsc().stream()
                .map(PaperStockResponse::from)
                .toList();
    }

    @Transactional
    public PaperStockResponse createForAdmin(String adminEmail, PaperStockRequest request) {
        authService.requireAdmin(adminEmail);
        var code = normalizeCode(request.code());
        if (paperStockRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paper code already exists");
        }
        var paper = new PaperStock(
                code,
                request.name().trim(),
                normalizeDescription(request.description()),
                request.gsm(),
                request.pricePer100(),
                request.status(),
                request.active()
        );
        return PaperStockResponse.from(paperStockRepository.save(paper));
    }

    @Transactional
    public PaperStockResponse updateForAdmin(Long id, String adminEmail, PaperStockRequest request) {
        authService.requireAdmin(adminEmail);
        var paper = paperStockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper not found"));
        var code = normalizeCode(request.code());
        if (paperStockRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paper code already exists");
        }
        paper.update(
                code,
                request.name().trim(),
                normalizeDescription(request.description()),
                request.gsm(),
                request.pricePer100(),
                request.status(),
                request.active()
        );
        return PaperStockResponse.from(paperStockRepository.save(paper));
    }

    @Transactional
    public void deleteForAdmin(Long id, String adminEmail) {
        authService.requireAdmin(adminEmail);
        var paper = paperStockRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper not found"));
        paperStockRepository.delete(paper);
    }

    public OrderPaper requireOrderable(String paperCode) {
        if (paperCode == null || paperCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paper is required");
        }
        var paper = paperStockRepository.findByCodeIgnoreCase(paperCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported paper option"));
        if (!paper.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported paper option");
        }
        if (!"IN_STOCK".equals(paper.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paper is out of stock");
        }
        return new OrderPaper(paper.id(), paper.code(), paper.name(), paper.pricePer100());
    }

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    public record OrderPaper(Long id, String code, String name, BigDecimal pricePer100) {
    }
}