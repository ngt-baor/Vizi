package com.example.vizi.upload;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByFileNameAndUser_EmailIgnoreCase(String fileName, String email);
}
