package com.kudos.onchain.utils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadPollUtil {

    private static final ExecutorService screenshotProcessExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public static ExecutorService getExecutor() {
        return screenshotProcessExecutor;
    }
}
