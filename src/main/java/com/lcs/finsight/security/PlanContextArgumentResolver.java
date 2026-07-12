package com.lcs.finsight.security;

import com.lcs.finsight.models.PlanMembership;
import com.lcs.finsight.models.User;
import com.lcs.finsight.services.PlanService;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Resolves a {@link PlanContext} controller parameter from the {@code {planId}} path
 * variable. Reads the acting user from the security context, verifies plan membership
 * via {@link PlanService#getMembership(Long, User)} and exposes plan + role. Membership
 * failures (NotAMember / PlanNotFound) propagate to the {@code GlobalExceptionHandler}.
 */
public class PlanContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final PlanService planService;

    public PlanContextArgumentResolver(PlanService planService) {
        this.planService = planService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PlanContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        @SuppressWarnings("unchecked")
        Map<String, String> uriVariables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

        if (uriVariables == null || !uriVariables.containsKey("planId")) {
            throw new IllegalArgumentException("Missing required path variable {planId}.");
        }

        Long planId = Long.parseLong(uriVariables.get("planId"));
        User user = resolveUser();

        PlanMembership membership = planService.getMembership(planId, user);
        return new PlanContext(membership.getPlan(), user, membership.getRole());
    }

    private User resolveUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getUser();
        }
        throw new IllegalStateException("No authenticated user in the security context.");
    }
}
