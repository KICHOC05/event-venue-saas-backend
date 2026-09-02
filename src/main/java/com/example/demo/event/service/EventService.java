package com.example.demo.event.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.cash.model.CashRegister;
import com.example.demo.cash.repository.CashRegisterRepository;
import com.example.demo.common.enums.CashStatus;
import com.example.demo.common.enums.EventStatus;
import com.example.demo.common.enums.PaymentMethod;
import com.example.demo.common.enums.ProductType;
import com.example.demo.document.model.DocumentType;
import com.example.demo.document.service.DocumentSequenceService;
import com.example.demo.event.dto.AvailabilityResponse;
import com.example.demo.event.dto.CreateEventRequest;
import com.example.demo.event.dto.EventCalendarResponse;
import com.example.demo.event.dto.EventPaymentResponse;
import com.example.demo.event.dto.EventRescheduleHistoryResponse;
import com.example.demo.event.dto.EventResponse;
import com.example.demo.event.dto.RegisterEventPaymentRequest;
import com.example.demo.event.dto.RescheduleEventRequest;
import com.example.demo.event.dto.UpdateEventRequest;
import com.example.demo.event.exception.ScheduleConflictException;
import com.example.demo.event.model.EventBooking;
import com.example.demo.event.model.EventPayment;
import com.example.demo.event.model.EventRescheduleHistory;
import com.example.demo.event.repository.EventBookingRepository;
import com.example.demo.event.repository.EventPaymentRepository;
import com.example.demo.event.repository.EventRescheduleHistoryRepository;
import com.example.demo.product.model.Product;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventBookingRepository eventBookingRepository;
    private final EventPaymentRepository eventPaymentRepository;
    private final EventRescheduleHistoryRepository eventRescheduleHistoryRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final DocumentSequenceService documentSequenceService;
    private final CashRegisterRepository cashRegisterRepository;

    private static final List<EventStatus> ACTIVE_STATUSES = List.of(
            EventStatus.PENDING_DEPOSIT,
            EventStatus.CONFIRMED,
            EventStatus.IN_PROGRESS
    );

    // =====================================================
    // VALIDACIONES DE NEGOCIO
    // =====================================================

    /**
     * Valida todas las reglas de negocio para un evento.
     */
    private void validateEventData(
            LocalDate eventDate,
            LocalTime startTime,
            LocalTime endTime,
            Integer childAge,
            Integer guestChildren,
            Integer guestAdults,
            BigDecimal depositAmount,
            BigDecimal eventPrice) {

        // Validación 1: Fecha obligatoria
        if (eventDate == null) {
            throw new IllegalArgumentException("La fecha del evento es obligatoria");
        }

        // Validación 2: Fecha en el pasado
        if (eventDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("No se pueden registrar eventos en fechas pasadas");
        }

        // Validación 3: Hora final debe ser mayor a hora inicio
        if (endTime == null || startTime == null) {
            throw new IllegalArgumentException("La hora de inicio y fin son obligatorias");
        }

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("La hora de finalización debe ser mayor a la hora de inicio");
        }

        // Validación 4: Duración mínima (30 minutos)
        Duration duration = Duration.between(startTime, endTime);
        if (duration.toMinutes() < 30) {
            throw new IllegalArgumentException("La duración mínima del evento es de 30 minutos");
        }

        // Validación 5: Duración máxima (12 horas)
        if (duration.toHours() > 12) {
            throw new IllegalArgumentException("La duración máxima permitida es de 12 horas");
        }

        // Validación 6: Anticipo negativo
        if (depositAmount != null && depositAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El anticipo no puede ser negativo");
        }

        // Validación 7: Anticipo mayor al precio del paquete
        if (depositAmount != null && eventPrice != null && depositAmount.compareTo(eventPrice) > 0) {
            throw new IllegalArgumentException("El anticipo no puede ser mayor al precio del evento");
        }

        // Validación 8: Edad del niño
        if (childAge != null && (childAge < 0 || childAge > 18)) {
            throw new IllegalArgumentException("Edad de niño inválida (debe estar entre 0 y 18 años)");
        }

        // Validación 9: Niños invitados
        if (guestChildren != null && guestChildren < 0) {
            throw new IllegalArgumentException("La cantidad de niños invitados no puede ser negativa");
        }

        // Validación 10: Adultos invitados
        if (guestAdults != null && guestAdults < 0) {
            throw new IllegalArgumentException("La cantidad de adultos invitados no puede ser negativa");
        }
    }

    /**
     * ✅ NUEVA VALIDACIÓN: Verifica si ya existe un evento en la misma fecha
     */
    private void validateUniqueDate(LocalDate eventDate) {
        Long tenantId = TenantContext.getTenantId();
        
        boolean exists = eventBookingRepository.existsByDateAndStatuses(
                tenantId,
                eventDate,
                ACTIVE_STATUSES
        );
        
        if (exists) {
            throw new ScheduleConflictException("Ya existe un evento programado para esta fecha. Solo se permite un evento por día.");
        }
    }

    /**
     * ✅ NUEVA VALIDACIÓN: Verifica si ya existe un evento en la misma fecha (excluyendo uno para updates)
     */
    private void validateUniqueDateExcluding(LocalDate eventDate, String excludePublicId) {
        Long tenantId = TenantContext.getTenantId();
        
        boolean exists = eventBookingRepository.existsByDateAndStatusesExcluding(
                tenantId,
                eventDate,
                ACTIVE_STATUSES,
                excludePublicId
        );
        
        if (exists) {
            throw new ScheduleConflictException("Ya existe un evento programado para esta fecha. Solo se permite un evento por día.");
        }
    }

    // =====================================================
    // VALIDACIÓN DE DISPONIBILIDAD POR HORARIO
    // =====================================================

    private void validateAvailability(LocalDate eventDate, LocalTime startTime, LocalTime endTime) {
        Long tenantId = TenantContext.getTenantId();
        
        List<EventBooking> conflicts = eventBookingRepository.findConflicts(
                tenantId,
                eventDate,
                startTime,
                endTime,
                ACTIVE_STATUSES
        );
        
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException("Ya existe un evento reservado en ese horario.");
        }
    }

    private void validateAvailabilityExcluding(LocalDate eventDate, LocalTime startTime, 
                                                LocalTime endTime, String excludePublicId) {
        Long tenantId = TenantContext.getTenantId();
        
        List<EventBooking> conflicts = eventBookingRepository.findConflictsExcluding(
                tenantId,
                eventDate,
                startTime,
                endTime,
                ACTIVE_STATUSES,
                excludePublicId
        );
        
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException("Ya existe un evento reservado en ese horario.");
        }
    }

    // =====================================================
    // CRUD
    // =====================================================

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        // ✅ VALIDACIÓN 1: Fecha única (no puede haber dos eventos en la misma fecha)
        validateUniqueDate(request.getEventDate());

        // ✅ VALIDACIÓN 2: Disponibilidad por horario
        validateAvailability(
                request.getEventDate(),
                request.getStartTime(),
                request.getEndTime()
        );

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant no encontrado"));

        Branch branch = branchRepository.findByIdAndTenant_Id(branchId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Sucursal no encontrada"));

        Product packageProduct = productRepository.findByPublicIdAndTenant_IdAndActiveTrue(
                        request.getPackageProductPublicId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        if (packageProduct.getType() != ProductType.PACKAGE) {
            throw new IllegalArgumentException("El producto seleccionado no es un paquete de evento");
        }

        BigDecimal depositAmount = request.getDepositAmount() != null 
                ? request.getDepositAmount() 
                : BigDecimal.ZERO;
        
        BigDecimal eventPrice = packageProduct.getPrice();
        BigDecimal remainingAmount = eventPrice.subtract(depositAmount);

        // Validaciones de negocio
        validateEventData(
                request.getEventDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getChildAge(),
                request.getGuestChildren(),
                request.getGuestAdults(),
                depositAmount,
                eventPrice
        );

        if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (request.getInitialPaymentMethod() == null) {
                throw new IllegalArgumentException(
                        "El método de pago inicial es obligatorio cuando el anticipo es mayor a 0");
            }
        }

        CashRegister initialPaymentCashRegister = depositAmount.compareTo(BigDecimal.ZERO) > 0
                ? requireOpenCashRegister(tenantId, branchId)
                : null;

        long eventNumber = documentSequenceService.nextNumber(
                tenant,
                branch,
                DocumentType.EVENT
        );

        EventBooking event = EventBooking.builder()
                .tenant(tenant)
                .branch(branch)
                .eventNumber(eventNumber)
                .packageProduct(packageProduct)
                .customerName(request.getCustomerName())
                .phone(request.getPhone())
                .childName(request.getChildName())
                .childAge(request.getChildAge())
                .eventDate(request.getEventDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .guestChildren(request.getGuestChildren())
                .guestAdults(request.getGuestAdults())
                .notes(request.getNotes())
                .eventPrice(eventPrice)
                .depositAmount(depositAmount)
                .remainingAmount(remainingAmount)
                .status(EventStatus.PENDING_DEPOSIT)
                .build();

        EventBooking savedEvent = eventBookingRepository.save(event);

        if (depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            EventPayment initialPayment = EventPayment.builder()
                    .eventBooking(savedEvent)
                    .tenant(tenant)
                    .branch(branch)
                    .cashRegister(initialPaymentCashRegister)
                    .amount(depositAmount)
                    .eventPriceAtPayment(eventPrice)
                    .paymentMethod(request.getInitialPaymentMethod())
                    .reference(request.getInitialPaymentReference())
                    .notes(request.getInitialPaymentNotes())
                    .receivedByUserPublicId(getCurrentUserPublicId())
                    .receivedByUserEmail(getCurrentUserEmail())
                    .build();

            eventPaymentRepository.save(initialPayment);
        }

        log.info("Evento creado: {} para cliente: {}", savedEvent.getPublicId(), savedEvent.getCustomerName());

        return mapToResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        return mapToResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEvents() {
        Long tenantId = TenantContext.getTenantId();

        return eventBookingRepository.findByTenant_Id(tenantId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public EventResponse updateEvent(String publicId, UpdateEventRequest request) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("No se puede modificar un evento cancelado");
        }

        if (event.getStatus() == EventStatus.IN_PROGRESS) {
            throw new IllegalStateException("No se puede modificar un evento en progreso");
        }

        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("No se puede modificar un evento completado");
        }

        // ✅ VALIDACIÓN 1: Fecha única (excluyendo el evento actual)
        validateUniqueDateExcluding(request.getEventDate(), publicId);

        // ✅ VALIDACIÓN 2: Disponibilidad por horario (excluyendo el evento actual)
        validateAvailabilityExcluding(
                request.getEventDate(),
                request.getStartTime(),
                request.getEndTime(),
                publicId
        );

        Product packageProduct = productRepository.findByPublicIdAndTenant_IdAndActiveTrue(
                        request.getPackageProductPublicId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));

        if (packageProduct.getType() != ProductType.PACKAGE) {
            throw new IllegalArgumentException("El producto seleccionado no es un paquete de evento");
        }

        BigDecimal depositAmount = request.getDepositAmount() != null 
                ? request.getDepositAmount() 
                : BigDecimal.ZERO;
        
        BigDecimal eventPrice = packageProduct.getPrice();
        BigDecimal remainingAmount = eventPrice.subtract(depositAmount);

        // Validaciones de negocio
        validateEventData(
                request.getEventDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getChildAge(),
                request.getGuestChildren(),
                request.getGuestAdults(),
                depositAmount,
                eventPrice
        );

        event.setPackageProduct(packageProduct);
        event.setCustomerName(request.getCustomerName());
        event.setPhone(request.getPhone());
        event.setChildName(request.getChildName());
        event.setChildAge(request.getChildAge());
        event.setEventDate(request.getEventDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setGuestChildren(request.getGuestChildren());
        event.setGuestAdults(request.getGuestAdults());
        event.setNotes(request.getNotes());
        event.setEventPrice(eventPrice);
        event.setDepositAmount(depositAmount);
        event.setRemainingAmount(remainingAmount);

        EventBooking updatedEvent = eventBookingRepository.save(event);
        log.info("Evento actualizado: {}", updatedEvent.getPublicId());

        return mapToResponse(updatedEvent);
    }

    @Transactional
    public void cancelEvent(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("El evento ya está cancelado");
        }

        event.setStatus(EventStatus.CANCELLED);
        eventBookingRepository.save(event);
        
        log.info("Evento cancelado: {}", event.getPublicId());
    }

    @Transactional
    public EventResponse confirmEvent(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (event.getStatus() == EventStatus.CONFIRMED) {
            throw new IllegalStateException("El evento ya está confirmado");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Eventos cancelados no pueden ser confirmados");
        }

        if (event.getStatus() == EventStatus.IN_PROGRESS) {
            throw new IllegalStateException("Eventos en progreso no pueden ser confirmados");
        }

        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("Eventos completados no pueden ser confirmados");
        }

        if (event.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("El evento no puede confirmarse hasta cubrir el monto total");
        }

        event.setStatus(EventStatus.CONFIRMED);
        EventBooking confirmedEvent = eventBookingRepository.save(event);

        log.info("Evento confirmado: {}", confirmedEvent.getPublicId());

        return mapToResponse(confirmedEvent);
    }

    @Transactional
    public EventResponse startEvent(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (event.getStatus() == EventStatus.IN_PROGRESS) {
            throw new IllegalStateException("El evento ya está en progreso");
        }

        if (event.getStatus() == EventStatus.PENDING_DEPOSIT) {
            throw new IllegalStateException("Eventos con depósito pendiente deben ser confirmados antes de iniciar");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Eventos cancelados no pueden ser iniciados");
        }

        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("Eventos completados no pueden ser iniciados");
        }

        event.setStatus(EventStatus.IN_PROGRESS);
        EventBooking startedEvent = eventBookingRepository.save(event);

        log.info("Evento iniciado: {}", startedEvent.getPublicId());

        return mapToResponse(startedEvent);
    }

    @Transactional
    public EventResponse completeEvent(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("El evento ya está completado");
        }

        if (event.getStatus() == EventStatus.PENDING_DEPOSIT) {
            throw new IllegalStateException("Eventos con depósito pendiente no pueden ser completados");
        }

        if (event.getStatus() == EventStatus.CONFIRMED) {
            throw new IllegalStateException("Eventos confirmados deben iniciarse antes de completarse");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Eventos cancelados no pueden ser completados");
        }

        event.setStatus(EventStatus.COMPLETED);
        EventBooking completedEvent = eventBookingRepository.save(event);

        log.info("Evento completado: {}", completedEvent.getPublicId());

        return mapToResponse(completedEvent);
    }

    @Transactional
    public EventResponse rescheduleEvent(String publicId, RescheduleEventRequest request) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (event.getStatus() == EventStatus.IN_PROGRESS) {
            throw new IllegalStateException("Eventos en progreso no pueden ser reagendados");
        }

        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("Eventos completados no pueden ser reagendados");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("Eventos cancelados no pueden ser reagendados");
        }

        // Validar fecha no pasada
        if (request.getEventDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("No se pueden reagendar eventos en fechas pasadas");
        }

        // Validar rango horario
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("La hora de finalización debe ser mayor a la hora de inicio");
        }

        // Validar duración mínima
        Duration duration = Duration.between(request.getStartTime(), request.getEndTime());
        if (duration.toMinutes() < 30) {
            throw new IllegalArgumentException("La duración mínima del evento es de 30 minutos");
        }

        // Validar duración máxima
        if (duration.toHours() > 12) {
            throw new IllegalArgumentException("La duración máxima permitida es de 12 horas");
        }

        // Validar fecha única
        validateUniqueDateExcluding(request.getEventDate(), publicId);

        // Validar disponibilidad por horario
        validateAvailabilityExcluding(
                request.getEventDate(),
                request.getStartTime(),
                request.getEndTime(),
                publicId
        );

        // Capturar valores anteriores antes de mutar el evento
        LocalDate oldEventDate = event.getEventDate();
        LocalTime oldStartTime = event.getStartTime();
        LocalTime oldEndTime = event.getEndTime();

        event.setEventDate(request.getEventDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());

        if (request.getReason() != null && !request.getReason().isBlank()) {
            String reasonEntry = "[Reagendado: " + request.getReason().trim() + "]";
            if (event.getNotes() != null && !event.getNotes().isBlank()) {
                event.setNotes(event.getNotes() + "\n" + reasonEntry);
            } else {
                event.setNotes(reasonEntry);
            }
        }

        EventBooking rescheduledEvent = eventBookingRepository.save(event);
        log.info("Evento reagendado: {} -> {}", rescheduledEvent.getPublicId(), rescheduledEvent.getEventDate());

        // Guardar historial de reagendamiento
        String changedByUserPublicId = null;
        String changedByUserEmail = null;
        Long userId = TenantContext.getUserId();
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                // Solo asignamos si encontramos al usuario
            });
        }
        // Resolver datos del usuario de forma más directa
        if (userId != null) {
            User currentUser = userRepository.findById(userId).orElse(null);
            if (currentUser != null) {
                changedByUserPublicId = currentUser.getPublicId();
                changedByUserEmail = currentUser.getEmail();
            }
        }

        EventRescheduleHistory history = EventRescheduleHistory.builder()
                .eventBooking(rescheduledEvent)
                .tenant(rescheduledEvent.getTenant())
                .branch(rescheduledEvent.getBranch())
                .oldEventDate(oldEventDate)
                .oldStartTime(oldStartTime)
                .oldEndTime(oldEndTime)
                .newEventDate(rescheduledEvent.getEventDate())
                .newStartTime(rescheduledEvent.getStartTime())
                .newEndTime(rescheduledEvent.getEndTime())
                .reason(request.getReason().trim())
                .changedByUserPublicId(changedByUserPublicId)
                .changedByUserEmail(changedByUserEmail)
                .build();

        eventRescheduleHistoryRepository.save(history);
        log.info("Historial de reagendamiento guardado para evento: {}", rescheduledEvent.getPublicId());

        return mapToResponse(rescheduledEvent);
    }

    @Transactional(readOnly = true)
    public List<EventRescheduleHistoryResponse> getRescheduleHistory(String publicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        return eventRescheduleHistoryRepository
                .findByEventBooking_PublicIdAndTenant_IdOrderByChangedAtDesc(publicId, tenantId)
                .stream()
                .map(this::mapToHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventCalendarResponse> getCalendar(LocalDate from, LocalDate to) {
        Long tenantId = TenantContext.getTenantId();
        
        List<EventBooking> events = eventBookingRepository
                .findByTenant_IdAndEventDateBetweenOrderByEventDateAscStartTimeAsc(tenantId, from, to);
        
        return events.stream()
                .map(this::mapToCalendarResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(LocalDate date, LocalTime start, LocalTime end) {
        return checkAvailability(date, start, end, null);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(LocalDate date, LocalTime start, LocalTime end,
                                                   String excludePublicId) {
        Long tenantId = TenantContext.getTenantId();

        // Validar regla de un evento por día
        boolean dateTaken;
        if (excludePublicId != null) {
            dateTaken = eventBookingRepository.existsByDateAndStatusesExcluding(
                    tenantId, date, ACTIVE_STATUSES, excludePublicId
            );
        } else {
            dateTaken = eventBookingRepository.existsByDateAndStatuses(
                    tenantId, date, ACTIVE_STATUSES
            );
        }

        if (dateTaken) {
            return AvailabilityResponse.builder()
                    .available(false)
                    .build();
        }

        // Validar conflictos por horario
        List<EventBooking> conflicts;
        if (excludePublicId != null) {
            conflicts = eventBookingRepository.findConflictsExcluding(
                    tenantId, date, start, end, ACTIVE_STATUSES, excludePublicId
            );
        } else {
            conflicts = eventBookingRepository.findConflicts(
                    tenantId, date, start, end, ACTIVE_STATUSES
            );
        }

        return AvailabilityResponse.builder()
                .available(conflicts.isEmpty())
                .build();
    }

    // =====================================================
    // PAGOS DE EVENTOS
    // =====================================================

    @Transactional
    public EventPaymentResponse registerEventPayment(String eventPublicId, RegisterEventPaymentRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        EventBooking event = eventBookingRepository.findByPublicId(eventPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        if (!event.getBranch().getId().equals(branchId)) {
            throw new SecurityException("El pago debe registrarse desde la sucursal del evento");
        }

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new IllegalStateException("No se pueden registrar pagos en eventos cancelados");
        }

        if (event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("No se pueden registrar pagos en eventos completados");
        }

        if (request.getPaymentMethod() != PaymentMethod.CASH
                && (request.getReference() == null || request.getReference().isBlank())) {
            throw new IllegalArgumentException(
                    "La referencia es obligatoria para pagos con tarjeta o transferencia"
            );
        }

        BigDecimal remaining = event.getEventPrice().subtract(event.getDepositAmount());

        if (request.getAmount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "El pago no puede exceder el saldo pendiente de $" + remaining
            );
        }

        // Resolver datos del usuario
        String receivedByUserPublicId = null;
        String receivedByUserEmail = null;
        Long userId = TenantContext.getUserId();
        if (userId != null) {
            User currentUser = userRepository.findById(userId).orElse(null);
            if (currentUser != null) {
                receivedByUserPublicId = currentUser.getPublicId();
                receivedByUserEmail = currentUser.getEmail();
            }
        }

        CashRegister cashRegister = requireOpenCashRegister(tenantId, branchId);

        // Crear el registro de pago
        EventPayment payment = EventPayment.builder()
                .eventBooking(event)
                .tenant(event.getTenant())
                .branch(event.getBranch())
                .cashRegister(cashRegister)
                .amount(request.getAmount())
                .eventPriceAtPayment(event.getEventPrice())
                .paymentMethod(request.getPaymentMethod())
                .reference(request.getReference())
                .notes(request.getNotes())
                .receivedByUserPublicId(receivedByUserPublicId)
                .receivedByUserEmail(receivedByUserEmail)
                .build();

        eventPaymentRepository.save(payment);

        // Actualizar montos del evento
        BigDecimal newDeposit = event.getDepositAmount().add(request.getAmount());
        event.setDepositAmount(newDeposit);
        event.setRemainingAmount(event.getEventPrice().subtract(newDeposit));
        eventBookingRepository.save(event);

        log.info("Pago registrado para evento {}: ${} con {}", eventPublicId, request.getAmount(), request.getPaymentMethod());

        EventPaymentResponse response = mapToPaymentResponse(payment);
        response.setPreviousBalance(remaining);
        response.setTotalPaid(newDeposit);
        response.setRemainingAmount(event.getRemainingAmount());
        response.setFullyPaid(event.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0);
        return response;
    }

    private CashRegister requireOpenCashRegister(Long tenantId, Long branchId) {
        return cashRegisterRepository
                .findByTenant_IdAndBranch_IdAndStatusForUpdate(
                        tenantId, branchId, CashStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay caja abierta en la sucursal para registrar el pago del evento"));
    }

    @Transactional(readOnly = true)
    public List<EventPaymentResponse> getEventPayments(String eventPublicId) {
        Long tenantId = TenantContext.getTenantId();

        EventBooking event = eventBookingRepository.findByPublicId(eventPublicId)
                .orElseThrow(() -> new EntityNotFoundException("Evento no encontrado"));

        if (!event.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("No tiene acceso a este evento");
        }

        return eventPaymentRepository
                .findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtDesc(eventPublicId, tenantId)
                .stream()
                .map(this::mapToPaymentResponse)
                .toList();
    }

    // =====================================================
    // AUDITORÍA DE CONSISTENCIA
    // =====================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditEventPaymentConsistency() {
        Long tenantId = TenantContext.getTenantId();

        List<EventBooking> events = eventBookingRepository.findByTenant_Id(tenantId)
                .stream()
                .filter(e -> e.getDepositAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        List<Map<String, Object>> inconsistent = new java.util.ArrayList<>();

        for (EventBooking event : events) {
            BigDecimal paymentsSum = eventPaymentRepository
                    .findByEventBooking_PublicIdAndTenant_IdOrderByPaidAtDesc(
                            event.getPublicId(), tenantId)
                    .stream()
                    .map(EventPayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal difference = event.getDepositAmount().subtract(paymentsSum);

            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("eventPublicId", event.getPublicId());
                item.put("eventNumber", String.format("EV-%06d", event.getEventNumber()));
                item.put("depositAmount", event.getDepositAmount());
                item.put("paymentsSum", paymentsSum);
                item.put("difference", difference);
                inconsistent.add(item);

                log.warn("Inconsistencia financiera: evento {} depositAmount={} paymentsSum={} diff={}",
                        event.getPublicId(), event.getDepositAmount(), paymentsSum, difference);
            }
        }

        return inconsistent;
    }

    private String getCurrentUserPublicId() {
        Long userId = TenantContext.getUserId();
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) return user.getPublicId();
        }
        return null;
    }

    private String getCurrentUserEmail() {
        Long userId = TenantContext.getUserId();
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) return user.getEmail();
        }
        return null;
    }

    // =====================================================
    // MAPPERS
    // =====================================================

    private EventResponse mapToResponse(EventBooking event) {
        return EventResponse.builder()
                .publicId(event.getPublicId())
                .eventNumber(event.getEventNumber())
                .customerName(event.getCustomerName())
                .phone(event.getPhone())
                .childName(event.getChildName())
                .childAge(event.getChildAge())
                .eventDate(event.getEventDate())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .guestChildren(event.getGuestChildren())
                .guestAdults(event.getGuestAdults())
                .notes(event.getNotes())
                .packageProductPublicId(event.getPackageProduct().getPublicId())
                .packageName(event.getPackageProduct().getName())
                .eventPrice(event.getEventPrice())
                .depositAmount(event.getDepositAmount())
                .remainingAmount(event.getRemainingAmount())
                .status(event.getStatus())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }

    private EventCalendarResponse mapToCalendarResponse(EventBooking event) {
        return EventCalendarResponse.builder()
                .publicId(event.getPublicId())
                .eventNumber(event.getEventNumber())
                .customerName(event.getCustomerName())
                .childName(event.getChildName())
                .eventDate(event.getEventDate())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .build();
    }

    private EventRescheduleHistoryResponse mapToHistoryResponse(EventRescheduleHistory history) {
        return EventRescheduleHistoryResponse.builder()
                .publicId(history.getPublicId())
                .oldEventDate(history.getOldEventDate())
                .oldStartTime(history.getOldStartTime())
                .oldEndTime(history.getOldEndTime())
                .newEventDate(history.getNewEventDate())
                .newStartTime(history.getNewStartTime())
                .newEndTime(history.getNewEndTime())
                .reason(history.getReason())
                .changedByUserPublicId(history.getChangedByUserPublicId())
                .changedByUserEmail(history.getChangedByUserEmail())
                .changedAt(history.getChangedAt())
                .build();
    }

    private EventPaymentResponse mapToPaymentResponse(EventPayment payment) {
        return EventPaymentResponse.builder()
                .publicId(payment.getPublicId())
                .eventPublicId(payment.getEventBooking().getPublicId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getPaymentMethod())
                .reference(payment.getReference())
                .notes(payment.getNotes())
                .receivedByUserPublicId(payment.getReceivedByUserPublicId())
                .receivedByUserEmail(payment.getReceivedByUserEmail())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
