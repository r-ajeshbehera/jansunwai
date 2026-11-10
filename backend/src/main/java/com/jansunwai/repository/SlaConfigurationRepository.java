package com.jansunwai.repository;

import com.jansunwai.entity.Priority;
import com.jansunwai.entity.SlaConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SlaConfigurationRepository extends JpaRepository<SlaConfiguration, Long> {
    @Query("select s from SlaConfiguration s where s.department.id = :departmentId and s.priority = :priority and s.active = true")
    Optional<SlaConfiguration> findForDepartment(@Param("departmentId") Long departmentId, @Param("priority") Priority priority);

    @Query("select s from SlaConfiguration s where s.department is null and s.priority = :priority and s.active = true")
    Optional<SlaConfiguration> findDefault(@Param("priority") Priority priority);

    @Query("select s from SlaConfiguration s left join fetch s.department order by s.department.name nulls first, s.priority")
    List<SlaConfiguration> findAllOrdered();

    @Query("""
           select count(s) > 0 from SlaConfiguration s
           where ((:departmentId is null and s.department is null) or s.department.id = :departmentId) and s.priority = :priority
           """)
    boolean existsFor(@Param("departmentId") Long departmentId, @Param("priority") Priority priority);
}
