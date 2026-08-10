package com.lilac.planner.contract;

import com.lilac.planner.persistence.PlannerStore;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Runs the {@link PlannerStoreContractTest} against the JPA adapter on H2. */
@SpringBootTest
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("PlannerStore contract - JPA (H2)")
class JpaPlannerStoreContractIT extends PlannerStoreContractTest {

    @Autowired
    PlannerStore store;

    @Override
    protected PlannerStore store() {
        return store;
    }
}
