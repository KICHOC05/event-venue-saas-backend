package com.example.demo.document.repository;

import com.example.demo.document.model.DocumentSequence;
import com.example.demo.document.model.DocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO document_sequences
                (tenant_id, branch_id, document_type, current_value)
            VALUES
                (:tenantId, :branchId, :documentType, 0)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void ensureExists(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("documentType") String documentType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT sequence
            FROM DocumentSequence sequence
            WHERE sequence.tenant.id = :tenantId
              AND sequence.branch.id = :branchId
              AND sequence.documentType = :documentType
            """)
    Optional<DocumentSequence> findForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("documentType") DocumentType documentType
    );
}
