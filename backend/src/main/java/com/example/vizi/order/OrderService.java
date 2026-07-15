package com.example.vizi.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.example.vizi.auth.AuthService;
import com.example.vizi.design.Design;
import com.example.vizi.design.DesignRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {

    private static final Map<String, PaperOption> PAPER_OPTIONS = Map.of(
            "matte-350", new PaperOption("Matte 350gsm", new BigDecimal("180000")),
            "silk-400", new PaperOption("Silk 400gsm", new BigDecimal("240000")),
            "linen-300", new PaperOption("Linen 300gsm", new BigDecimal("280000"))
    );
    private static final BigDecimal ROUNDED_CORNERS_PRICE_PER_100 = new BigDecimal("30000");

    private final OrderRepository orderRepository;
    private final DesignRepository designRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    OrderService(
            OrderRepository orderRepository,
            DesignRepository designRepository,
            AuthService authService,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.designRepository = designRepository;
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String email) {
        var user = authService.requireUser(email);
        var design = designRepository.findByIdAndUser_Id(request.designId(), user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Design not found"));
        var paper = PAPER_OPTIONS.get(request.paper());
        if (paper == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported paper option");
        }
        if (request.quantity() % 100 != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be a multiple of 100");
        }

        var total = calculateTotal(request.quantity(), paper, request.roundedCorners());
        var order = new ViziOrder(user, "PENDING_PAYMENT", total, normalizeNote(request.customerNote()));
        order.addItem(new OrderItem(
                order,
                design,
                design.canvasJson(),
                printConfigJson(request, paper, design),
                request.quantity(),
                total.divide(BigDecimal.valueOf(request.quantity()), 2, RoundingMode.HALF_UP),
                total
        ));
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOwnedOrder(Long orderId, String email) {
        var user = authService.requireUser(email);
        return orderRepository.findByIdAndUser_Id(orderId, user.id())
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listAllOrdersForAdmin(String adminEmail) {
        authService.requireAdmin(adminEmail);
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForAdmin(Long orderId, String adminEmail) {
        authService.requireAdmin(adminEmail);
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Transactional
    public OrderResponse updateOrderStatusForAdmin(Long orderId, String status, String adminEmail) {
        authService.requireAdmin(adminEmail);
        var allowed = java.util.Set.of(
                "DRAFT", "PENDING_PAYMENT", "PAID", "PRINTING", "DONE", "CANCELLED"
        );
        if (status == null || !allowed.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status");
        }
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        order.updateStatus(status);
        return OrderResponse.from(orderRepository.save(order));
    }

    private static BigDecimal calculateTotal(int quantity, PaperOption paper, boolean roundedCorners) {
        var batches = BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(100), 0, RoundingMode.UP);
        var total = paper.pricePer100().multiply(batches);
        return roundedCorners ? total.add(ROUNDED_CORNERS_PRICE_PER_100.multiply(batches)) : total;
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private String printConfigJson(CreateOrderRequest request, PaperOption paper, Design design) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "paper", request.paper(),
                    "paperLabel", paper.label(),
                    "quantity", request.quantity(),
                    "roundedCorners", request.roundedCorners(),
                    "widthMm", design.widthMm(),
                    "heightMm", design.heightMm()
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize print config", exception);
        }
    }

    private record PaperOption(String label, BigDecimal pricePer100) {
    }
}
