package com.example.vizi.order;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<ViziOrder, Long> {
}
