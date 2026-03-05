package com.pred.apitests.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * TestNG listener: logs test start, success, failure, skip. No access to request/response; use Filters for that.
 */
public class TestListener implements ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        LOG.info("Test started: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOG.info("Test passed: {}", result.getMethod().getMethodName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOG.warn("Test failed: {} - {}", result.getMethod().getMethodName(), result.getThrowable() != null ? result.getThrowable().getMessage() : "");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOG.info("Test skipped: {}", result.getMethod().getMethodName());
    }
}
