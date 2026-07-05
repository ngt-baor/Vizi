package com.example.vizi;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface DesignRepository extends JpaRepository<Design, Long> {

    Optional<Design> findByIdAndUser_Id(Long id, Long userId);
}
