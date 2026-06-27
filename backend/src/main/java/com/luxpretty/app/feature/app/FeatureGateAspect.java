package com.luxpretty.app.feature.app;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class FeatureGateAspect {

    private final FeatureFlagService flags;

    public FeatureGateAspect(FeatureFlagService flags) {
        this.flags = flags;
    }

    @Around("@within(com.luxpretty.app.feature.app.RequiresFeature) "
          + "|| @annotation(com.luxpretty.app.feature.app.RequiresFeature)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        RequiresFeature ann = m.getAnnotation(RequiresFeature.class);
        if (ann == null) {
            ann = m.getDeclaringClass().getAnnotation(RequiresFeature.class);
        }
        if (ann != null) {
            var key = ann.value();
            if (!flags.isEnabled(key)) {
                throw new FeatureDisabledException(key, TierFeatureCatalog.minimumTierFor(key));
            }
        }
        return pjp.proceed();
    }
}
