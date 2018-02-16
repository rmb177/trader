package com.coinbase.exchange.api.marketdata;

public interface MarketDataServiceInterface
{
    public MarketData getMarketDataOrderBook(String productId, String level); 
}
