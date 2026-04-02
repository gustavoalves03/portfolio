package com.prettyface.app.multitenancy;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Runs repository work against the shared application schema, even when a
 * tenant-scoped request already has a TenantContext set.
 */
@Component
public class ApplicationSchemaExecutor {

    private final TransactionTemplate requiresNewTransaction;

    public ApplicationSchemaExecutor(PlatformTransactionManager transactionManager) {
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T call(Supplier<T> action) {
        String previousTenant = TenantContext.getCurrentTenant();

        try {
            TenantContext.clear();
            return requiresNewTransaction.execute(status -> action.get());
        } finally {
            restoreTenant(previousTenant);
        }
    }

    public void run(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }

    private void restoreTenant(String previousTenant) {
        if (previousTenant == null || previousTenant.isBlank()) {
            TenantContext.clear();
            return;
        }

        TenantContext.setCurrentTenant(previousTenant);
    }
}
