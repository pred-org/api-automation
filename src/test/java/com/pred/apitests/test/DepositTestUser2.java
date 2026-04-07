package com.pred.apitests.test;

import com.pred.apitests.util.SecondUserContext;
import com.pred.apitests.util.UserSession;
import org.testng.SkipException;

/**
 * Same as DepositTest but runs for user 2 (SecondUserContext).
 * Deposit is skipped when User 2 balance is already sufficient (same as User 1).
 * Requires AuthFlowTestUser2 to have run first so .env.session2 exists.
 */
public class DepositTestUser2 extends DepositTest {

    @Override
    protected UserSession getSession() {
        UserSession s = SecondUserContext.getSecondUser();
        if (s == null || !s.hasToken()) {
            throw new SkipException("User 2 session not set - run AuthFlowTestUser2 first");
        }
        return s;
    }
}
