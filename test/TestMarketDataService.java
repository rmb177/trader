package test;

import com.coinbase.exchange.api.marketdata.MarketData;
import com.coinbase.exchange.api.marketdata.MarketDataServiceInterface;
import com.coinbase.exchange.api.marketdata.OrderItem;

import java.util.ArrayList;
import java.util.List;

public class TestMarketDataService implements MarketDataServiceInterface 
{
    private List<OrderItem> bids;
    private List<OrderItem> asks;

    private int numCalls = 0; 

    
    public TestMarketDataService(List<OrderItem> bids, List<OrderItem> asks)
    {
        this.bids = bids;
        this.asks = asks;
    }


    public MarketData getMarketDataOrderBook(String productId, String level) 
    {
        List<OrderItem> retBid = new ArrayList<OrderItem>();
        List<OrderItem> retAsk = new ArrayList<OrderItem>();

        if (bids.size() > numCalls)
        {
            retBid.add(bids.get(numCalls));
        }

        if (asks.size() > numCalls)
        {
            retAsk.add(asks.get(numCalls));
        }

        numCalls += 1;

        return new MarketData(new Long(1), retBid, retAsk);
    }
}
