package com.example.vizi.design;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignRepository extends JpaRepository<Design, Long> {

    @EntityGraph(attributePaths = "user")
    List<Design> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<Design> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<Design> findWithUserById(Long id);
}
