package com.lcs.finsight.support;

import com.lcs.finsight.models.User;
import com.lcs.finsight.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestComponent
public class TestAuthHelper {

    @Autowired
    private JwtService jwtService;

    public String bearerFor(User user) {
        return "Bearer " + jwtService.generateToken(user.getEmail());
    }

    public RequestPostProcessor asUser(User user) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, bearerFor(user));
            return request;
        };
    }
}
