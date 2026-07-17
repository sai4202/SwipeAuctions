package com.swipeauctions.catalog.repository;

import com.swipeauctions.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIsNull();

    List<Category> findByParent_Id(UUID parentId);

    boolean existsBySlug(String slug);
}
