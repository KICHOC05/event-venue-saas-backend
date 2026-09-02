package com.example.demo.loyalty.service;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.client.model.Client;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.loyalty.dto.ClientLoyaltyResponse;
import com.example.demo.loyalty.dto.LoyaltyProgramRequest;
import com.example.demo.loyalty.dto.LoyaltyProgramResponse;
import com.example.demo.loyalty.dto.RedeemRewardRequest;
import com.example.demo.loyalty.model.ClientLoyaltyProgress;
import com.example.demo.loyalty.model.ClientLoyaltyVisit;
import com.example.demo.loyalty.model.ClientRewardRedemption;
import com.example.demo.loyalty.model.LoyaltyProgram;
import com.example.demo.loyalty.model.RedemptionStatus;
import com.example.demo.loyalty.repository.ClientLoyaltyProgressRepository;
import com.example.demo.loyalty.repository.ClientLoyaltyVisitRepository;
import com.example.demo.loyalty.repository.ClientRewardRedemptionRepository;
import com.example.demo.loyalty.repository.LoyaltyProgramRepository;
import com.example.demo.order.model.Order;
import com.example.demo.order.model.OrderItem;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyProgramRepository programRepository;
    private final ClientLoyaltyVisitRepository visitRepository;
    private final ClientRewardRedemptionRepository redemptionRepository;
    private final ClientLoyaltyProgressRepository progressRepository;
    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LoyaltyProgramResponse getProgram() {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        Optional<LoyaltyProgram> opt = programRepository.findByTenant_IdAndBranch_Id(tenantId, branchId);

        return opt.map(this::mapProgramToResponse)
                .orElseGet(() -> createDefaultAndReturn(tenantId, branchId));
    }

    @Transactional
    public LoyaltyProgramResponse updateProgram(LoyaltyProgramRequest request) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        LoyaltyProgram program = programRepository
                .findByTenant_IdAndBranch_Id(tenantId, branchId)
                .orElseGet(() -> createDefault(tenantId, branchId));

        if (request.getName() != null) program.setName(request.getName());
        if (request.getDescription() != null) program.setDescription(request.getDescription());
        if (request.getQualifyingProductPublicId() != null) {
            Product product = productRepository
                    .findByPublicIdAndTenant_IdAndActiveTrue(request.getQualifyingProductPublicId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found"));
            program.setQualifyingProduct(product);
        }
        if (request.getRequiredPurchases() != null && request.getRequiredPurchases() > 0) {
            program.setRequiredPurchases(request.getRequiredPurchases());
        }
        if (request.getRewardQuantity() != null && request.getRewardQuantity() > 0) {
            program.setRewardQuantity(request.getRewardQuantity());
        }
        if (request.getRewardDescription() != null) program.setRewardDescription(request.getRewardDescription());
        if (request.getActive() != null) program.setActive(request.getActive());
        program.setUpdatedAt(LocalDateTime.now());

        programRepository.save(program);

        return mapProgramToResponse(program);
    }

    @Transactional(readOnly = true)
    public ClientLoyaltyResponse getClientLoyalty(String clientPublicId) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();

        Client client = clientRepository.findByPublicIdAndTenant_Id(clientPublicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        LoyaltyProgram program = programRepository
                .findByTenant_IdAndBranch_Id(tenantId, branchId)
                .orElse(null);

        if (program == null || !Boolean.TRUE.equals(program.getActive())) {
            return emptyResponse();
        }

        return buildLoyaltyResponse(client, program);
    }

    @Transactional
    public void registerVisits(Order order) {
        if (order == null) {
            log.warn("LOYALTY_HOOK order=NULL decision=SKIP_NULL_ORDER");
            return;
        }
        log.info("LOYALTY_HOOK order={} status={} hasClient={} clientId={}",
                order.getPublicId(), order.getStatus(),
                order.getClient() != null,
                order.getClient() != null ? order.getClient().getId() : "N/A");
        registerVisitsInternal(order);
    }

    private void registerVisitsInternal(Order order) {
        if (order == null) {
            log.warn("LOYALTY_AUDIT order=NULL decision=SKIP_NULL_ORDER");
            return;
        }

        Long tenantId = order.getTenant() != null ? order.getTenant().getId() : null;
        Long branchId = order.getBranch() != null ? order.getBranch().getId() : null;
        boolean hasClient = order.getClient() != null;
        Long clientId = hasClient ? order.getClient().getId() : null;
        boolean clientFrequent = hasClient && Boolean.TRUE.equals(order.getClient().getFrequent());

        final LoyaltyProgram program;
        if (tenantId != null && branchId != null) {
            program = programRepository
                    .findByTenant_IdAndBranch_Id(tenantId, branchId)
                    .orElse(null);
        } else {
            program = null;
        }
        boolean programFound = program != null;
        boolean programActive = program != null && Boolean.TRUE.equals(program.getActive());
        Long programId = program != null ? program.getId() : null;
        Long qualifyingProductId = (program != null && program.getQualifyingProduct() != null)
                ? program.getQualifyingProduct().getId()
                : null;

        List<OrderItem> allItems = orderItemRepository.findAllByOrder_Id(order.getId());
        long qualifyingCount = (qualifyingProductId != null)
                ? allItems.stream()
                    .filter(i -> i.getProduct() != null)
                    .filter(i -> i.getProduct().getId().equals(qualifyingProductId))
                    .filter(i -> com.example.demo.common.enums.OrderItemStatus.ACTIVE.equals(i.getStatus()))
                    .filter(i -> !Boolean.TRUE.equals(i.getRewardItem()))
                    .filter(i -> i.getSubtotal() != null)
                    .filter(i -> i.getSubtotal().compareTo(BigDecimal.ZERO) > 0)
                    .count()
                : 0;

        log.info("LOYALTY_AUDIT order={} status={} tenantId={} branchId={} hasClient={} clientId={} clientFrequent={} programFound={} programActive={} programId={} qualifyingProductId={} totalItems={} qualifyingItems={}",
                order.getPublicId(), order.getStatus(), tenantId, branchId,
                hasClient, clientId, clientFrequent,
                programFound, programActive, programId, qualifyingProductId,
                allItems.size(), qualifyingCount);

        if (!com.example.demo.common.enums.OrderStatus.CLOSED.equals(order.getStatus())) {
            log.info("LOYALTY_DECISION order={} decision=SKIP_ORDER_NOT_CLOSED status={}",
                    order.getPublicId(), order.getStatus());
            return;
        }

        if (!hasClient) {
            log.info("LOYALTY_DECISION order={} decision=SKIP_NO_CLIENT", order.getPublicId());
            return;
        }

        if (!clientFrequent) {
            log.info("LOYALTY_DECISION order={} decision=SKIP_CLIENT_NOT_FREQUENT clientId={}",
                    order.getPublicId(), clientId);
            return;
        }

        if (!programFound) {
            log.warn("LOYALTY_DECISION order={} decision=SKIP_NO_ACTIVE_PROGRAM tenantId={} branchId={}",
                    order.getPublicId(), tenantId, branchId);
            return;
        }

        if (!programActive) {
            log.info("LOYALTY_DECISION order={} decision=SKIP_PROGRAM_NOT_ACTIVE programId={}",
                    order.getPublicId(), programId);
            return;
        }

        if (qualifyingProductId == null) {
            log.warn("LOYALTY_DECISION order={} decision=SKIP_NO_QUALIFYING_PRODUCT programId={}",
                    order.getPublicId(), programId);
            return;
        }

        if (qualifyingCount == 0) {
            log.info("LOYALTY_DECISION order={} decision=SKIP_NO_QUALIFYING_ITEMS totalItems={}",
                    order.getPublicId(), allItems.size());
            return;
        }

        Client client = order.getClient();
        int visitsCreated = 0;
        for (OrderItem item : allItems) {
            boolean matches = item.getProduct() != null
                    && item.getProduct().getId().equals(qualifyingProductId)
                    && com.example.demo.common.enums.OrderItemStatus.ACTIVE.equals(item.getStatus())
                    && !Boolean.TRUE.equals(item.getRewardItem())
                    && item.getSubtotal() != null
                    && item.getSubtotal().compareTo(BigDecimal.ZERO) > 0;

            if (matches) {
                if (visitRepository.existsByOrderItem_Id(item.getId())) {
                    log.info("LOYALTY_DECISION order={} decision=SKIP_DUPLICATE itemId={}",
                            order.getPublicId(), item.getPublicId());
                    continue;
                }

                ClientLoyaltyVisit visit = new ClientLoyaltyVisit();
                visit.setTenant(order.getTenant());
                visit.setBranch(order.getBranch());
                visit.setClient(client);
                visit.setLoyaltyProgram(program);
                visit.setOrder(order);
                visit.setOrderItem(item);
                visit.setVisitDate(LocalDateTime.now());
                visit.setQualifying(true);

                visitRepository.save(visit);
                visitsCreated++;
                log.info("LOYALTY_DECISION order={} decision=CREATE_VISIT item={} product={}",
                        order.getPublicId(), item.getPublicId(), item.getProduct().getName());
            }
        }

        if (visitsCreated > 0) {
            ClientLoyaltyProgress progress = progressRepository
                    .findByClient_IdAndLoyaltyProgram_Id(client.getId(), program.getId())
                    .orElseGet(() -> {
                        ClientLoyaltyProgress p = new ClientLoyaltyProgress();
                        p.setClient(client);
                        p.setLoyaltyProgram(program);
                        p.setTenant(order.getTenant());
                        p.setBranch(order.getBranch());
                        p.setCurrentCount(0);
                        p.setRequiredCount(program.getRequiredPurchases());
                        p.setRewardsEarned(0L);
                        p.setRewardsAvailable(0L);
                        p.setRewardsRedeemed(0L);
                        return p;
                    });

            int newCount = (progress.getCurrentCount() != null ? progress.getCurrentCount() : 0) + visitsCreated;
            int required = program.getRequiredPurchases();
            long earned = (progress.getRewardsEarned() != null ? progress.getRewardsEarned() : 0L);
            long available = (progress.getRewardsAvailable() != null ? progress.getRewardsAvailable() : 0L);

            if (newCount >= required) {
                earned += newCount / required;
                available += newCount / required;
                newCount = newCount % required;
            }

            progress.setCurrentCount(newCount);
            progress.setRequiredCount(required);
            progress.setRewardsEarned(earned);
            progress.setRewardsAvailable(available);
            progress.setLastVisitAt(LocalDateTime.now());
            progress.setUpdatedAt(LocalDateTime.now());

            progressRepository.save(progress);
            log.info("LOYALTY_DECISION order={} decision=CREATE_VISITS_DONE count={} progress={}/{} earned={} available={}",
                    order.getPublicId(), visitsCreated, newCount, required, earned, available);
        } else {
            log.info("LOYALTY_DECISION order={} decision=NO_VISITS_CREATED", order.getPublicId());
        }
    }

    @Transactional
    public void redeemReward(RedeemRewardRequest request, String orderPublicId) {
        Long tenantId = TenantContext.getTenantId();
        Long branchId = TenantContext.getBranchId();
        Long userId = TenantContext.getUserId();

        Client client = clientRepository.findByPublicIdAndTenant_Id(request.getClientPublicId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found"));

        LoyaltyProgram program = programRepository
                .findByTenant_IdAndBranch_Id(tenantId, branchId)
                .orElseThrow(() -> new EntityNotFoundException("Loyalty program not found"));

        if (!Boolean.TRUE.equals(program.getActive())) {
            throw new IllegalStateException("El programa de lealtad no está activo");
        }

        ClientLoyaltyResponse loyalty = buildLoyaltyResponse(client, program);

        if (loyalty.getRewardsAvailable() <= 0) {
            throw new IllegalStateException("No hay recompensas disponibles");
        }

        Order order = orderRepository
                .findByPublicIdAndTenant_Id(orderPublicId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        OrderItem rewardItem = new OrderItem();
        rewardItem.setOrder(order);
        rewardItem.setProduct(program.getQualifyingProduct());
        rewardItem.setQuantity(1);
        rewardItem.setUnitPrice(BigDecimal.ZERO);
        rewardItem.setSubtotal(BigDecimal.ZERO);
        rewardItem.setStatus(com.example.demo.common.enums.OrderItemStatus.ACTIVE);
        rewardItem.setRewardItem(true);

        if (client.getChildName() != null && !client.getChildName().isBlank()) {
            rewardItem.setChildName(client.getChildName());
        }

        orderItemRepository.save(rewardItem);

        ClientRewardRedemption redemption = new ClientRewardRedemption();
        redemption.setTenant(order.getTenant());
        redemption.setBranch(order.getBranch());
        redemption.setClient(client);
        redemption.setLoyaltyProgram(program);
        redemption.setOrder(order);
        redemption.setRedeemedBy(user);
        redemption.setRedeemedAt(LocalDateTime.now());
        redemption.setStatus(RedemptionStatus.REDEEMED);

        redemptionRepository.save(redemption);
    }

    private ClientLoyaltyResponse buildLoyaltyResponse(Client client, LoyaltyProgram program) {
        ClientLoyaltyProgress progress = progressRepository
                .findByClient_IdAndLoyaltyProgram_Id(client.getId(), program.getId())
                .orElse(null);

        if (progress == null) {
            long totalVisits = visitRepository.countByClient_IdAndLoyaltyProgram_Id(client.getId(), program.getId());
            long redeemed = redemptionRepository.countByClient_IdAndLoyaltyProgram_IdAndStatus(
                    client.getId(), program.getId(), RedemptionStatus.REDEEMED);
            int required = program.getRequiredPurchases();
            long rewardsEarned = totalVisits / required;
            long rewardsAvailable = Math.max(0, rewardsEarned - redeemed);

            ClientLoyaltyResponse r = new ClientLoyaltyResponse();
            r.setTotalVisits(totalVisits);
            r.setRequiredPurchases(required);
            r.setRewardsEarned(rewardsEarned);
            r.setRewardsAvailable(rewardsAvailable);
            r.setRewardsRedeemed(redeemed);
            r.setNextRewardAt(required - (int) (totalVisits % required));
            return r;
        }

        long redeemed = redemptionRepository.countByClient_IdAndLoyaltyProgram_IdAndStatus(
                client.getId(), program.getId(), RedemptionStatus.REDEEMED);
        int currentCount = progress.getCurrentCount() != null ? progress.getCurrentCount() : 0;
        int required = program.getRequiredPurchases();
        long rewardsAvailable = progress.getRewardsAvailable() != null
                ? Math.max(0, progress.getRewardsAvailable() - redeemed)
                : 0;

        ClientLoyaltyResponse r = new ClientLoyaltyResponse();
        r.setTotalVisits(currentCount);
        r.setRequiredPurchases(required);
        r.setRewardsEarned(progress.getRewardsEarned() != null ? progress.getRewardsEarned() : 0);
        r.setRewardsAvailable(rewardsAvailable);
        r.setRewardsRedeemed(redeemed);
        r.setNextRewardAt(required - currentCount);
        return r;
    }

    private ClientLoyaltyResponse emptyResponse() {
        ClientLoyaltyResponse r = new ClientLoyaltyResponse();
        r.setTotalVisits(0);
        r.setRequiredPurchases(5);
        r.setRewardsEarned(0);
        r.setRewardsAvailable(0);
        r.setRewardsRedeemed(0);
        r.setNextRewardAt(5);
        return r;
    }

    @Transactional
    private LoyaltyProgramResponse createDefaultAndReturn(Long tenantId, Long branchId) {
        LoyaltyProgram program = createDefault(tenantId, branchId);
        return mapProgramToResponse(program);
    }

    private LoyaltyProgram createDefault(Long tenantId, Long branchId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found"));

        LoyaltyProgram program = new LoyaltyProgram();
        program.setTenant(tenant);
        program.setBranch(branch);
        program.setName("Cliente frecuente");
        program.setDescription("Compra horas de juego y recibe horas gratis");
        program.setRequiredPurchases(5);
        program.setRewardQuantity(1);
        program.setRewardDescription("1 hora de juego gratis");
        program.setActive(false);

        programRepository.save(program);
        return program;
    }

    private LoyaltyProgramResponse mapProgramToResponse(LoyaltyProgram program) {
        LoyaltyProgramResponse r = new LoyaltyProgramResponse();
        r.setPublicId(program.getPublicId());
        r.setName(program.getName());
        r.setDescription(program.getDescription());
        if (program.getQualifyingProduct() != null) {
            r.setQualifyingProductPublicId(program.getQualifyingProduct().getPublicId());
            r.setQualifyingProductName(program.getQualifyingProduct().getName());
        }
        r.setRequiredPurchases(program.getRequiredPurchases());
        r.setRewardQuantity(program.getRewardQuantity());
        r.setRewardDescription(program.getRewardDescription());
        r.setActive(program.getActive());
        return r;
    }
}
