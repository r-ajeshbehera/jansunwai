package com.jansunwai.repository;

import com.jansunwai.entity.Role;
import com.jansunwai.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u left join fetch u.department where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u left join fetch u.department where u.id = :id")
    Optional<User> findWithDepartment(@Param("id") Long id);

    List<User> findByDepartment_IdAndRoleAndActiveTrueOrderByFullName(Long departmentId, Role role);

    List<User> findByRoleAndActiveTrue(Role role);

    @Query(value = """
           select u from User u left join fetch u.department d
           where (:role is null or u.role = :role)
             and (:departmentId is null or d.id = :departmentId)
             and (:q is null or lower(u.fullName) like concat('%', :q, '%') or lower(u.email) like concat('%', :q, '%'))
           """,
           countQuery = """
           select count(u) from User u left join u.department d
           where (:role is null or u.role = :role)
             and (:departmentId is null or d.id = :departmentId)
             and (:q is null or lower(u.fullName) like concat('%', :q, '%') or lower(u.email) like concat('%', :q, '%'))
           """)
    Page<User> search(@Param("role") Role role, @Param("departmentId") Long departmentId, @Param("q") String q, Pageable pageable);
}
