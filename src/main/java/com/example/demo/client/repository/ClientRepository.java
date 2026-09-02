package com.example.demo.client.repository;

import com.example.demo.client.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByPublicIdAndTenant_Id(String publicId, Long tenantId);

    Optional<Client> findFirstByTenant_IdAndPhoneOrderByIdAsc(Long tenantId, String phone);

    @Query("SELECT c FROM Client c " +
           "WHERE c.tenant.id = :tenantId " +
           "AND c.status = 'ACTIVE' " +
           "AND (:search IS NULL " +
               "OR LOWER(COALESCE(c.parentName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
               "OR LOWER(COALESCE(c.childName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
               "OR LOWER(COALESCE(c.phone,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
               "OR LOWER(COALESCE(c.email,'')) LIKE LOWER(CONCAT('%',:search,'%'))) " +
           "AND (:frequent IS NULL OR c.frequent = :frequent) " +
           "ORDER BY c.parentName ASC, c.childName ASC")
    Page<Client> searchByTenant(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("frequent") Boolean frequent,
            Pageable pageable);

    @Query("SELECT c FROM Client c " +
           "WHERE c.tenant.id = :tenantId " +
           "AND c.branch.id = :branchId " +
           "AND c.status = 'ACTIVE' " +
           "AND (:search IS NULL " +
               "OR LOWER(COALESCE(c.parentName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
               "OR LOWER(COALESCE(c.childName,'')) LIKE LOWER(CONCAT('%',:search,'%')) " +
               "OR LOWER(COALESCE(c.phone,'')) LIKE LOWER(CONCAT('%',:search,'%')))")
    List<Client> quickSearchByTenantAndBranch(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("search") String search);
}
