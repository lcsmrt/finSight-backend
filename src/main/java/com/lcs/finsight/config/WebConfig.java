package com.lcs.finsight.config;

import com.lcs.finsight.security.PlanContextArgumentResolver;
import com.lcs.finsight.services.PlanService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PlanService planService;

    public WebConfig(PlanService planService) {
        this.planService = planService;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new PlanContextArgumentResolver(planService));
    }
}
