package com.jansunwai.repository;

import com.jansunwai.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WardRepository extends JpaRepository<Ward, Long> {
    List<Ward> findAllByOrderByCode();

    boolean existsByCode(String code);
}
