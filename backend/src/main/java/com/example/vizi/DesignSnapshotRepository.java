package com.example.vizi;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface DesignSnapshotRepository extends JpaRepository<DesignSnapshot, Long> {

    long countByDesign_Id(Long designId);

    List<DesignSnapshot> findByDesign_IdOrderByIdAsc(Long designId);
}
