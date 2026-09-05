package com.example.demo.loyalty;

import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.client.repository.ClientRepository;
import com.example.demo.common.enums.OrderStatus;
import com.example.demo.loyalty.repository.ClientLoyaltyProgressRepository;
import com.example.demo.loyalty.repository.ClientLoyaltyVisitRepository;
import com.example.demo.loyalty.repository.ClientRewardRedemptionRepository;
import com.example.demo.loyalty.repository.LoyaltyProgramRepository;
import com.example.demo.loyalty.service.LoyaltyService;
import com.example.demo.order.model.Order;
import com.example.demo.order.repository.OrderItemRepository;
import com.example.demo.order.repository.OrderRepository;
import com.example.demo.product.repository.ProductRepository;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LoyaltyServiceCheckoutOptimizationTest {

    @Test
    void closedOrderWithoutClientSkipsProgramAndItemQueries() {
        LoyaltyProgramRepository programRepository = mock(LoyaltyProgramRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        LoyaltyService service = new LoyaltyService(
                programRepository,
                mock(ClientLoyaltyVisitRepository.class),
                mock(ClientRewardRedemptionRepository.class),
                mock(ClientLoyaltyProgressRepository.class),
                mock(ClientRepository.class),
                mock(OrderRepository.class),
                orderItemRepository,
                mock(ProductRepository.class),
                mock(TenantRepository.class),
                mock(BranchRepository.class),
                mock(UserRepository.class));
        Order order = new Order();
        order.setPublicId("order-without-client");
        order.setStatus(OrderStatus.CLOSED);

        service.registerVisits(order, List.of());

        verifyNoInteractions(programRepository, orderItemRepository);
    }
}
