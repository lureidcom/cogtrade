package com.cogtrade.client;

public class ClientMarketItem {
    private final int id;
    private final String itemId;
    private final String displayName;
    private final String category;
    private final double price;
    private final int stock;

    public ClientMarketItem(int id, String itemId, String displayName,
                            String category, double price, int stock) {
        this.id = id;
        this.itemId = itemId;
        this.displayName = displayName;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    public int getId() { return id; }
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
}