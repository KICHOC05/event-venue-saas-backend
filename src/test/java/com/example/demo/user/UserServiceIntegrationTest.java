package com.example.demo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.demo.branch.model.Branch;
import com.example.demo.branch.repository.BranchRepository;
import com.example.demo.common.enums.UserRole;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.security.TenantContext;
import com.example.demo.tenant.model.Tenant;
import com.example.demo.tenant.repository.TenantRepository;
import com.example.demo.user.dto.UserResponse;
import com.example.demo.user.model.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.UserService;

@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Tenant tenant;
    private Branch branch;
    private User user;

    @BeforeEach
    void setUp() {
        tenant = createTenant("Tenant principal");
        branch = createBranch(tenant, "Sucursal principal");
        user = createUser(tenant, branch, "Administrador");

        TenantContext.set(new TenantContext.TenantInfo(
                tenant.getId(), branch.getId(), user.getId(), UserRole.ADMIN));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findAllReturnsBranchDataWithoutOpenSessionInViewOrNPlusOne() {
        Statistics statistics = statistics();
        statistics.clear();

        List<UserResponse> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPublicId()).isEqualTo(user.getPublicId());
        assertThat(result.getFirst().getBranchId()).isEqualTo(branch.getId());
        assertThat(result.getFirst().getBranchName()).isEqualTo(branch.getName());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void findByPublicIdReturnsBranchDataAndRejectsAnotherTenantUser() {
        Tenant otherTenant = createTenant("Tenant aislado");
        Branch otherBranch = createBranch(otherTenant, "Sucursal aislada");
        User otherUser = createUser(otherTenant, otherBranch, "Usuario aislado");

        UserResponse ownUser = userService.findByPublicId(user.getPublicId());

        assertThat(ownUser.getBranchId()).isEqualTo(branch.getId());
        assertThat(ownUser.getBranchName()).isEqualTo(branch.getName());
        assertThatThrownBy(() -> userService.findByPublicId(otherUser.getPublicId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuario no encontrado");
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        return statistics;
    }

    private Tenant createTenant(String prefix) {
        Tenant value = new Tenant();
        value.setBusinessName(prefix + " " + UUID.randomUUID());
        return tenantRepository.save(value);
    }

    private Branch createBranch(Tenant owner, String prefix) {
        Branch value = new Branch();
        value.setTenant(owner);
        value.setName(prefix + " " + UUID.randomUUID());
        return branchRepository.save(value);
    }

    private User createUser(Tenant owner, Branch assignedBranch, String name) {
        User value = new User();
        value.setTenant(owner);
        value.setBranch(assignedBranch);
        value.setName(name);
        value.setEmail(UUID.randomUUID() + "@example.test");
        value.setPassword("test-password");
        value.setRole(UserRole.ADMIN);
        return userRepository.save(value);
    }
}
