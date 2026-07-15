package com.example.vizi.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<ViziOrder, Long> {
    Optional<ViziOrder> findByIdAndUser_Id(Long id, Long userId);

    List<ViziOrder> findAllByOrderByCreatedAtDesc();
}
