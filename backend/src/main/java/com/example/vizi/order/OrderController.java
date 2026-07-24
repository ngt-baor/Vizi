package com.example.vizi.order;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication
    ) {
        return orderService.createOrder(request, authentication.getName());
    }

    @GetMapping
    List<OrderResponse> listOrders(Authentication authentication) {
        return orderService.listOwnedOrders(authentication.getName());
    }

    @GetMapping("/{orderId}")
    OrderResponse getOrder(@PathVariable Long orderId, Authentication authentication) {
        return orderService.getOwnedOrder(orderId, authentication.getName());
    }
}
