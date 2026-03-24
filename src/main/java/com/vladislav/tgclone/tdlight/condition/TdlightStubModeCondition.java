package com.vladislav.tgclone.tdlight.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class TdlightStubModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isEnabled(context) && "STUB".equalsIgnoreCase(currentMode(context));
    }

    private boolean isEnabled(ConditionContext context) {
        return Boolean.parseBoolean(context.getEnvironment().getProperty("app.tdlight.enabled", "false"));
    }

    private String currentMode(ConditionContext context) {
        return context.getEnvironment().getProperty("app.tdlight.public-channel-client-mode", "STUB");
    }
}
