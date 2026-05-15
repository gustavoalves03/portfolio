package com.luxpretty.app.subscription.repo;

import com.luxpretty.app.subscription.domain.StripeEventProcessed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeEventRepository extends JpaRepository<StripeEventProcessed, String> {
}
