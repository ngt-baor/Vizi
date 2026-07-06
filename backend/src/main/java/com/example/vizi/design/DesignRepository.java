package com.example.vizi.design;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface DesignRepository extends JpaRepository<Design, Long> {

    List<Design> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<Design> findByIdAndUser_Id(Long id, Long userId);
}
