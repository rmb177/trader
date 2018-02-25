package test;

import com.coinbase.exchange.api.entity.NewLimitOrderSingle;

import ui.TraderWindow;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Stack;


/**
* Mock test object for ui that does nothing
*/
public class TestTraderWindow extends TraderWindow
{
    public TestTraderWindow()
    {
    }


    public Integer getNextPollingInterval()
    {
        return 0;
    }


    public void setErrorText(String message)
    {
    }


    public void setMarketData(BigDecimal moneyAvailable, BigDecimal highestBid, BigDecimal currentBid, BigDecimal currentAsk)
    {
    }
         


    public void setLastUpdatedText(Date date)
    {
    }


    public void displayCurrentBuyOrder(NewLimitOrderSingle buyOrder)
    {
    }


    public void displayCurrentSellOrders(Stack<NewLimitOrderSingle> orders)
    {
    }


    public void writeOrderToScreen(NewLimitOrderSingle order, Date date)
    {
    } 
}
