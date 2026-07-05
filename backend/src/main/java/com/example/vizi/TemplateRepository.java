package com.example.vizi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface TemplateRepository extends JpaRepository<Template, Long> {

    List<Template> findByActiveTrueOrderByIdAsc();

    Optional<Template> findByIdAndActiveTrue(Long id);
}
