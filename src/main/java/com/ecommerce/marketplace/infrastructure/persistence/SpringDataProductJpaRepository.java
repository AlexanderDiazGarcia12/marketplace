package com.ecommerce.marketplace.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataProductJpaRepository extends JpaRepository<JPAProductEntity, Long> {

    Optional<JPAProductEntity> findBySku(String sku);
}
