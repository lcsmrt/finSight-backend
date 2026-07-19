package com.lcs.finsight.utils;

public class ApiRoutes {
    public static final String BASE = "/api/finsight";
    public static final String AUTH = BASE + "/auth";
    public static final String USER = BASE + "/user";
    public static final String PLAN = BASE + "/plan";
    public static final String INVITATION = BASE + "/invitation";

    public static final String PLAN_INVITATION = PLAN + "/{planId}/invitation";
    public static final String FINANCIAL_TRANSACTION = PLAN + "/{planId}/financial-transaction";
    public static final String FINANCIAL_TRANSACTION_CATEGORY = PLAN + "/{planId}/financial-transaction-category";
    public static final String DASHBOARD = PLAN + "/{planId}/dashboard";

    private ApiRoutes() {
    }
}
