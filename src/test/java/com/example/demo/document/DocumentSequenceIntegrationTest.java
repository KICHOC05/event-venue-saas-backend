package com.example.demo.document;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.ProductType;
import com.example.demo.common.enums.TenantStatus;
import com.example.demo.document.model.DocumentSequence;
import com.example.demo.document.model.DocumentType;
import com.example.demo.document.repository.DocumentSequenceRepository;
import com.example.demo.document.service.DocumentSequenceService;
import com.example.demo.event.migration.EventNumberMigrationService;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class DocumentSequenceIntegrationTest {

    @Autowired
    private DocumentSequenceService sequenceService;

    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Autowired
    private EventNumberMigrationService migrationService;

    @Autowired
    private EventBookingRepository eventBookingRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        eventBookingRepository.deleteAll();
        productRepository.deleteAll();
        sequenceRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void concurrentFirstNumbersAreUniqueForSameTenantAndBranch() throws Exception {
        Tenant tenant = createTenant("Tenant A");
        Branch branch = createBranch(tenant, "Sucursal 1");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = executor.submit(() -> nextConcurrently(tenant, branch, ready, start));
            Future<Long> second = executor.submit(() -> nextConcurrently(tenant, branch, ready, start));
            ready.await();
            start.countDown();

            assertEquals(Set.of(1L, 2L), Set.of(first.get(), second.get()));
            assertEquals(2L, onlySequence().getCurrentValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sequencesAreIndependentAcrossTenantAndBranch() {
        Tenant tenantA = createTenant("Tenant A");
        Branch branchA1 = createBranch(tenantA, "Sucursal 1");
        Branch branchA2 = createBranch(tenantA, "Sucursal 2");
        Tenant tenantB = createTenant("Tenant B");
        Branch branchB1 = createBranch(tenantB, "Sucursal 7");

        assertEquals(1L, next(tenantA, branchA1));
        assertEquals(2L, next(tenantA, branchA1));
        assertEquals(3L, next(tenantA, branchA1));
        assertEquals(1L, next(tenantA, branchA2));
        assertEquals(2L, next(tenantA, branchA2));
        assertEquals(1L, next(tenantB, branchB1));
    }

    @Test
    void rolledBackIncrementIsReused() {
        Tenant tenant = createTenant("Tenant A");
        Branch branch = createBranch(tenant, "Sucursal 1");

        assertThrows(ForcedRollbackException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            sequenceService.nextNumber(tenant, branch, DocumentType.EVENT);
            throw new ForcedRollbackException();
        }));

        assertEquals(1L, next(tenant, branch));
    }

    @Test
    void cancelledNumberIsNotReleased() {
        Tenant tenant = createTenant("Tenant A");
        Branch branch = createBranch(tenant, "Sucursal 1");
        Product product = createPackage(tenant);

        long cancelledEventNumber = next(tenant, branch);
        EventBooking cancelledEvent = createEvent(
                tenant,
                branch,
                product,
                "Evento cancelado",
                cancelledEventNumber,
                10
        );
        cancelledEvent.setStatus(EventStatus.CANCELLED);
        eventBookingRepository.saveAndFlush(cancelledEvent);

        assertEquals(1L, cancelledEventNumber);
        assertEquals(2L, next(tenant, branch));
    }

    @Test
    void duplicateEventNumberIsRejectedWithinSameBranch() {
        Tenant tenant = createTenant("Tenant A");
        Branch branch = createBranch(tenant, "Sucursal 1");
        Product product = createPackage(tenant);
        createEvent(tenant, branch, product, "Evento A", 1L, 10);

        assertThrows(DataIntegrityViolationException.class, () ->
                createEvent(tenant, branch, product, "Evento B", 1L, 11));
    }

    @Test
    void historicalMigrationIsDeterministicAndIdempotent() {
        Tenant tenant = createTenant("Tenant A");
        Branch branch = createBranch(tenant, "Sucursal 1");
        Product product = createPackage(tenant);
        createHistoricalEvent(tenant, branch, product, "A");
        createHistoricalEvent(tenant, branch, product, "B");
        createHistoricalEvent(tenant, branch, product, "C");

        EventNumberMigrationService.MigrationResult firstRun = migrationService.migrateHistoricalEvents();
        List<EventBooking> migrated = eventBookingRepository.findAll().stream()
                .sorted(Comparator.comparing(EventBooking::getId))
                .toList();

        assertEquals(3, firstRun.migratedEvents());
        assertEquals(List.of(1L, 2L, 3L), migrated.stream()
                .map(EventBooking::getEventNumber)
                .toList());
        assertEquals(3L, onlySequence().getCurrentValue());

        EventNumberMigrationService.MigrationResult secondRun = migrationService.migrateHistoricalEvents();
        assertEquals(0, secondRun.migratedEvents());
        assertEquals(List.of(1L, 2L, 3L), eventBookingRepository.findAll().stream()
                .sorted(Comparator.comparing(EventBooking::getId))
                .map(EventBooking::getEventNumber)
                .toList());
        assertEquals(3L, onlySequence().getCurrentValue());
    }

    private long nextConcurrently(
            Tenant tenant,
            Branch branch,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return next(tenant, branch);
    }

    private long next(Tenant tenant, Branch branch) {
        Long result = transactionTemplate.execute(status ->
                sequenceService.nextNumber(tenant, branch, DocumentType.EVENT));
        if (result == null) {
            throw new IllegalStateException("La transacción no devolvió un consecutivo");
        }
        return result;
    }

    private DocumentSequence onlySequence() {
        List<DocumentSequence> sequences = sequenceRepository.findAll();
        assertEquals(1, sequences.size());
        return sequences.getFirst();
    }

    private Tenant createTenant(String businessName) {
        Tenant tenant = new Tenant();
        tenant.setBusinessName(businessName);
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.saveAndFlush(tenant);
    }

    private Branch createBranch(Tenant tenant, String name) {
        Branch branch = new Branch();
        branch.setTenant(tenant);
        branch.setName(name);
        return branchRepository.saveAndFlush(branch);
    }

    private Product createPackage(Tenant tenant) {
        Product product = new Product();
        product.setTenant(tenant);
        product.setName("Paquete de prueba");
        product.setPrice(new BigDecimal("1000.00"));
        product.setType(ProductType.PACKAGE);
        product.setActive(true);
        product.setDepartment("Eventos");
        return productRepository.saveAndFlush(product);
    }

    private void createHistoricalEvent(
            Tenant tenant,
            Branch branch,
            Product product,
            String customerName
    ) {
        EventBooking event = EventBooking.builder()
                .tenant(tenant)
                .branch(branch)
                .packageProduct(product)
                .customerName(customerName)
                .childName("Niño " + customerName)
                .eventDate(LocalDate.now().plusDays(10))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .eventPrice(new BigDecimal("1000.00"))
                .depositAmount(BigDecimal.ZERO)
                .remainingAmount(new BigDecimal("1000.00"))
                .status(EventStatus.PENDING_DEPOSIT)
                .build();
        eventBookingRepository.saveAndFlush(event);
    }

    private EventBooking createEvent(
            Tenant tenant,
            Branch branch,
            Product product,
            String customerName,
            Long eventNumber,
            int daysFromNow
    ) {
        EventBooking event = EventBooking.builder()
                .tenant(tenant)
                .branch(branch)
                .eventNumber(eventNumber)
                .packageProduct(product)
                .customerName(customerName)
                .childName("Niño")
                .eventDate(LocalDate.now().plusDays(daysFromNow))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .eventPrice(new BigDecimal("1000.00"))
                .depositAmount(BigDecimal.ZERO)
                .remainingAmount(new BigDecimal("1000.00"))
                .status(EventStatus.PENDING_DEPOSIT)
                .build();
        return eventBookingRepository.saveAndFlush(event);
    }

    private static class ForcedRollbackException extends RuntimeException {
    }
}
