package com.lcs.finsight.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link com.lcs.finsight.models.User} into a controller handler,
 * resolved from the {@link CustomUserDetails} principal that {@link JwtAuthenticationFilter}
 * already placed in the security context — no extra database lookup. This is the account-level
 * counterpart to {@link PlanContext} for endpoints that are not plan-scoped.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "user")
public @interface CurrentUser {
}
