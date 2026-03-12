package com.cogtrade.client;

public class ClientEconomyData {

    private static double balance = 0;
    private static double dailyEarned = 0;
    private static double dailySpent = 0;
    private static boolean initialized = false;

    public static double getBalance() { return balance; }
    public static double getDailyEarned() { return dailyEarned; }
    public static double getDailySpent() { return dailySpent; }
    public static boolean isInitialized() { return initialized; }

    public static void update(double balance, double dailyEarned, double dailySpent) {
        ClientEconomyData.balance = balance;
        ClientEconomyData.dailyEarned = dailyEarned;
        ClientEconomyData.dailySpent = dailySpent;
        ClientEconomyData.initialized = true;
    }

    public static void reset() {
        balance = 0;
        dailyEarned = 0;
        dailySpent = 0;
        initialized = false;
    }
}