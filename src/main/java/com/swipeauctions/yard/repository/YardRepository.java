package com.swipeauctions.yard.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.swipeauctions.yard.entity.Yard;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface YardRepository extends JpaRepository<Yard, UUID> {

    Optional<Yard> findByYardCode(String yardCode);

    boolean existsByYardCode(String yardCode);

}