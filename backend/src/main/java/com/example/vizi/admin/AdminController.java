package com.example.vizi.admin;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.example.vizi.auth.AuthService;
import com.example.vizi.auth.User;
import com.example.vizi.auth.UserRepository;
import com.example.vizi.design.Design;
import com.example.vizi.design.DesignRepository;
import com.example.vizi.order.OrderResponse;
import com.example.vizi.order.OrderService;
import com.example.vizi.template.AdminTemplateItem;
import com.example.vizi.template.AdminTemplateRequest;
import com.example.vizi.template.TemplateService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final OrderService orderService;
    private final TemplateService templateService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final DesignRepository designRepository;

    AdminController(
            OrderService orderService,
            TemplateService templateService,
            AuthService authService,
            UserRepository userRepository,
            DesignRepository designRepository
    ) {
        this.orderService = orderService;
        this.templateService = templateService;
        this.authService = authService;
        this.userRepository = userRepository;
        this.designRepository = designRepository;
    }

    // ---- Orders (admin receives all orders) ----

    @GetMapping("/orders")
    List<OrderResponse> listOrders(Authentication authentication) {
        return orderService.listAllOrdersForAdmin(authentication.getName());
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse getOrder(@PathVariable Long orderId, Authentication authentication) {
        return orderService.getOrderForAdmin(orderId, authentication.getName());
    }

    @PutMapping("/orders/{orderId}/status")
    OrderResponse updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            Authentication authentication
    ) {
        return orderService.updateOrderStatusForAdmin(orderId, request.status(), authentication.getName());
    }

    // ---- Templates (publish / unpublish) ----

    @GetMapping("/templates")
    List<AdminTemplateItem> listTemplates(Authentication authentication) {
        return templateService.listAllTemplatesForAdmin(authentication.getName());
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    AdminTemplateItem createTemplate(
            @Valid @RequestBody AdminTemplateRequest request,
            Authentication authentication
    ) {
        return templateService.createTemplateForAdmin(authentication.getName(), request);
    }

    @PutMapping("/templates/{id}")
    AdminTemplateItem updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody AdminTemplateRequest request,
            Authentication authentication
    ) {
        return templateService.updateTemplateForAdmin(id, authentication.getName(), request);
    }

    @PutMapping("/templates/{id}/active")
    AdminTemplateItem setTemplateActive(
            @PathVariable Long id,
            @Valid @RequestBody SetActiveRequest request,
            Authentication authentication
    ) {
        return templateService.setTemplateActiveForAdmin(id, request.active(), authentication.getName());
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unpublishTemplate(@PathVariable Long id, Authentication authentication) {
        templateService.deleteTemplateForAdmin(id, authentication.getName());
    }

    // ---- User albums (view user designs) ----

    @GetMapping("/users")
    List<AdminUserItem> listUsers(Authentication authentication) {
        authService.requireAdmin(authentication.getName());
        return userRepository.findAll().stream()
                .map(user -> AdminUserItem.from(user, designRepository.countByUser_Id(user.id())))
                .sorted(Comparator.comparingLong(AdminUserItem::designCount).reversed()
                        .thenComparing(AdminUserItem::id))
                .toList();
    }

    @GetMapping("/users/{userId}/designs")
    List<AdminDesignItem> listUserDesigns(@PathVariable Long userId, Authentication authentication) {
        authService.requireAdmin(authentication.getName());
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return designRepository.findByUser_IdOrderByUpdatedAtDesc(userId).stream()
                .map(AdminDesignItem::from)
                .toList();
    }

    @GetMapping("/designs/{designId}")
    AdminDesignDetail getDesign(@PathVariable Long designId, Authentication authentication) {
        authService.requireAdmin(authentication.getName());
        var design = designRepository.findWithUserById(designId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        return AdminDesignDetail.from(design);
    }
}

record UpdateOrderStatusRequest(@NotBlank String status) {
}

record SetActiveRequest(boolean active) {
}

record AdminUserItem(
        Long id,
        String email,
        String fullName,
        String role,
        String phone,
        String address,
        long designCount
) {
    static AdminUserItem from(User user, long designCount) {
        return new AdminUserItem(
                user.id(),
                user.email(),
                user.fullName(),
                user.role(),
                user.phone(),
                user.address(),
                designCount
        );
    }
}

record AdminDesignItem(
        Long id,
        Long userId,
        String name,
        java.math.BigDecimal widthMm,
        java.math.BigDecimal heightMm,
        Instant updatedAt
) {
    static AdminDesignItem from(Design design) {
        return new AdminDesignItem(
                design.id(),
                design.userId(),
                design.name(),
                design.widthMm(),
                design.heightMm(),
                design.updatedAt()
        );
    }
}

record AdminDesignDetail(
        Long id,
        Long userId,
        String userEmail,
        String name,
        String canvasJson,
        java.math.BigDecimal widthMm,
        java.math.BigDecimal heightMm,
        Instant updatedAt
) {
    static AdminDesignDetail from(Design design) {
        return new AdminDesignDetail(
                design.id(),
                design.userId(),
                design.userEmail(),
                design.name(),
                design.canvasJson(),
                design.widthMm(),
                design.heightMm(),
                design.updatedAt()
        );
    }
}
