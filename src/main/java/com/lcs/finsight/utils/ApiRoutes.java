package com.lcs.finsight.utils;

public class ApiRoutes {
    public static final String BASE = "/api/finsight";
    public static final String AUTH = BASE + "/auth";
    public static final String USER = BASE + "/users";
    public static final String PLAN = BASE + "/plans";
    public static final String INVITATION = BASE + "/invitations";
    public static final String PLAN_INVITATION = PLAN + "/{planId}/invitations";
    public static final String FINANCIAL_TRANSACTION = PLAN + "/{planId}/financial-transaction";
    public static final String FINANCIAL_TRANSACTION_CATEGORY = PLAN + "/{planId}/financial-transaction-category";
    public static final String DASHBOARD = PLAN + "/{planId}/dashboard";

    private ApiRoutes() {
    }
}
