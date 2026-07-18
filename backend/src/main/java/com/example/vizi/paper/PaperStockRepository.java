package com.example.vizi.paper;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaperStockRepository extends JpaRepository<PaperStock, Long> {

    List<PaperStock> findByActiveTrueOrderByIdAsc();

    List<PaperStock> findAllByOrderByIdAsc();

    Optional<PaperStock> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}