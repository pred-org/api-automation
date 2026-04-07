package com.pred.apitests.test;

import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.UserSession;
import org.testng.SkipException;

/**
 * Same as CancelOrderTest but runs as user 2 (SecondUserContext).
 * Requires AuthFlowTestUser2 to have run first so .env.session2 exists.
 */
public class CancelOrderTestUser2 extends CancelOrderTest {

    @Override
    protected UserSession getSession() {
        UserSession s = SecondUserContext.getSecondUser();
        if (s == null || !s.hasToken()) {
            throw new SkipException("User 2 session not set - run AuthFlowTestUser2 first");
        }
        return s;
    }
}
