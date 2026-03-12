package com.cogtrade.market;

public class MarketItem {
    private final int id;
    private final String itemId;
    private final String displayName;
    private final String category;
    private double price;
    private int stock;
    private final int maxStock;
    private boolean enabled;

    public MarketItem(int id, String itemId, String displayName, String category,
                      double price, int stock, int maxStock, boolean enabled) {
        this.id = id;
        this.itemId = itemId;
        this.displayName = displayName;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.maxStock = maxStock;
        this.enabled = enabled;
    }

    public int getId() { return id; }
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public int getMaxStock() { return maxStock; }
    public boolean isEnabled() { return enabled; }
    public void setPrice(double price) { this.price = price; }
    public void setStock(int stock) { this.stock = stock; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}