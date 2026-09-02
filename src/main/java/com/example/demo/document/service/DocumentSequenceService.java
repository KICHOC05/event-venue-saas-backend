package com.example.demo.document.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.document.model.DocumentSequence;
import com.example.demo.document.model.DocumentType;
import com.example.demo.document.repository.DocumentSequenceRepository;
import com.example.demo.tenant.model.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentSequenceService {

    private final DocumentSequenceRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public long nextNumber(Tenant tenant, Branch branch, DocumentType documentType) {
        DocumentSequence sequence = lockSequence(tenant, branch, documentType);
        long nextValue = Math.addExact(sequence.getCurrentValue(), 1L);
        sequence.setCurrentValue(nextValue);
        repository.save(sequence);
        return nextValue;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureCurrentValueAtLeast(
            Tenant tenant,
            Branch branch,
            DocumentType documentType,
            long minimumValue
    ) {
        DocumentSequence sequence = lockSequence(tenant, branch, documentType);
        if (sequence.getCurrentValue() < minimumValue) {
            sequence.setCurrentValue(minimumValue);
            repository.save(sequence);
        }
    }

    private DocumentSequence lockSequence(
            Tenant tenant,
            Branch branch,
            DocumentType documentType
    ) {
        repository.ensureExists(tenant.getId(), branch.getId(), documentType.name());
        return repository.findForUpdate(tenant.getId(), branch.getId(), documentType)
                .orElseThrow(() -> new IllegalStateException(
                        "No fue posible inicializar la secuencia " + documentType));
    }
}
