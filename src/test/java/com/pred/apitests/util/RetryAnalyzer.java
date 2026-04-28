package com.pred.apitests.util;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class RetryAnalyzer implements IRetryAnalyzer {
    private int count = 0;
    private static final int MAX_RETRY = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (count < MAX_RETRY) {
            count++;
            System.out.printf("[RETRY] Retrying '%s' (attempt %d of %d)%n",
                    result.getMethod().getMethodName(), count + 1, MAX_RETRY + 1);
            return true;
        }
        return false;
    }
}
