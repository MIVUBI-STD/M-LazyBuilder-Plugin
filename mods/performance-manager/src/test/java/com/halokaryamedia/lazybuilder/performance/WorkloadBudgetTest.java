package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadBudgetTest {
    @Test
    void normalAllowsAllWork() {
        WorkloadBudget budget = new WorkloadBudget();
        budget.update(FramePressure.NORMAL);

        assertTrue(budget.allows(WorkClass.CRITICAL));
        assertTrue(budget.allows(WorkClass.NORMAL));
        assertTrue(budget.allows(WorkClass.DEFERRED));
    }

    @Test
    void elevatedDefersDeferredWork() {
        WorkloadBudget budget = new WorkloadBudget();
        budget.update(FramePressure.ELEVATED);

        assertTrue(budget.allows(WorkClass.CRITICAL));
        assertTrue(budget.allows(WorkClass.NORMAL));
        assertFalse(budget.allows(WorkClass.DEFERRED));
    }

    @Test
    void heavyAllowsCriticalWorkOnly() {
        WorkloadBudget budget = new WorkloadBudget();
        budget.update(FramePressure.HEAVY);

        assertTrue(budget.allows(WorkClass.CRITICAL));
        assertFalse(budget.allows(WorkClass.NORMAL));
        assertFalse(budget.allows(WorkClass.DEFERRED));
    }
}
