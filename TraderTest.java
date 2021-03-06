import com.coinbase.exchange.api.accounts.AccountServiceInterface;
import com.coinbase.exchange.api.entity.NewLimitOrderSingle;
import com.coinbase.exchange.api.marketdata.MarketDataServiceInterface;
import com.coinbase.exchange.api.marketdata.OrderItem;
import com.coinbase.exchange.api.orders.Order;
import com.coinbase.exchange.api.orders.OrderServiceInterface;

import test.TestAccountService;
import test.TestMarketDataService;
import test.TestOrderService;
import test.TestTraderWindow;

import ui.TraderWindow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


public class TraderTest
{
    TraderWindow mTraderWindow;

    public TraderTest()
    {
        mTraderWindow = new TestTraderWindow();        
    }


    public void runTests()
    {
        runTestForMultipleOpenBuysFromExchange();
        runTestForDifferentBuyOrderFromExchange();
        runTestForOneOpenBuyOrderFromExchange();
        runTestSortingSellOrdersAppropriatelyAtStartup();
        runTestForSeriesOfBuysUsingPricesOfPreviousOrdersToDriveNewLimits();
        runTestForSeriesOfBuysUsingMarketPricesToDriveNewLimits();
        runTestForInitialBuyAfterBasePercentageDropFromHighestPriceSeen();
        runTestForSeriesOfBuysThenASellUsingPricesOfPreviousOrdersToDriveNewLimits();
        runTestForSeriesOfBuysThenASellUsingMarketPricesToDriveNewLimits();
        runTestForNotEnoughBitcoinsInOrder();
        runTestForNotEnoughCash();
        runTestForCancelingBuyAfterRunUp();
    }


    /**
    * Runs processing loop the given number of times.
    */
    private void runTestLoop(Trader trader, int numIterations)
    {
        try
        {
            for (int x = 0; x < numIterations; ++x)
            {
                trader.checkOrderStatus();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    /**
    * Bot only handles one buy order at a time. If the exchange returns multiple
    * buy orders, something is wrong and we cancel all open buy orders.
    */
    private void runTestForMultipleOpenBuysFromExchange()
    {
        System.out.println("Running runTestForMultipleOpenBuysFromExchange ...");

        AccountServiceInterface accountService = setupTestAccountService(new double[] {});
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(new double[] {}, new double[] {});
    
        String[][] orders = new String[][] {{"1", "buy", "10000", "0.001"}, {"2", "buy", "11000", "0.002"}};
        OrderServiceInterface orderService = setupTestOrderService(orders);
       
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 1);
        assert(orderService.getOpenOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() == null);
    }



    /**
    * If the bot has an open buy order, it should have the same id as one
    * returned from the exchange. If not we cancel open buy order.
    */
    private void runTestForDifferentBuyOrderFromExchange()
    {
        System.out.println("Running runTestForDifferentBuyOrderFromExchange ...");
        
        AccountServiceInterface accountService = setupTestAccountService(new double[] {});
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(new double[] {}, new double[] {});
        
        String[][] orders = new String[][] {{"1", "buy", "10000", "0.001"}};
        OrderServiceInterface orderService = setupTestOrderService(orders);

        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 1);

        // We now have a current buy order in the bot.
        // Outside the bot we'll cancel the order and create a new order so the
        // exchange returns a different buy order to trigger our test.
        orderService.cancelOrder("1");

        assert(orderService.getOpenOrders().size() == 0);

        NewLimitOrderSingle newOrder = new NewLimitOrderSingle();
        newOrder.setSide("buy");
        newOrder.setProduct_id("BTC-USD");
        newOrder.setType("limit");
        newOrder.setPost_only(true);
        newOrder.setSize(new BigDecimal(.02));
        newOrder.setPrice(new BigDecimal(5000));
        orderService.createOrder(newOrder);

        assert(orderService.getOpenOrders().size() == 1);

        runTestLoop(trader, 1);
 
        assert(orderService.getOpenOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() == null);
    }


    /**
    * Test that if the exchange returns an open buy order the bot
    * has it as the current buy order.
    */ 
    private void runTestForOneOpenBuyOrderFromExchange()
    {
        System.out.println("Running runTestForOneOpenBuyOrderFromExchange ...");
        
        AccountServiceInterface accountService = setupTestAccountService(new double[] {});
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(new double[] {}, new double[] {});
        
        String[][] orders = new String[][] {{"1", "buy", "10000", "0.001"}};
        OrderServiceInterface orderService = setupTestOrderService(orders);

        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 1);
        
        assert(orderService.getOpenOrders().size() == 1);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.001);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10000);
    }



    /**
    * Test we put sell orders from the exchange in the right order on the 
    * stack (sells should be in ascending order from top to bottom). We probably
    * don't need to test this because we read in all outstanding sell orders
    * from disk at startup (that we wrote out at last program exit). But keeping
    * it here anyway.
    */
    private void runTestSortingSellOrdersAppropriatelyAtStartup()
    {
        System.out.println("Running runTestSortingSellOrdersAppropriatelyAtStartup ...");
        
        AccountServiceInterface accountService = setupTestAccountService(new double[] {});
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(new double[] {}, new double[] {});
        
        String[][] orders = new String[][] {{"1", "sell", "8200", "0.001"},
                                            {"2", "sell", "8100", "0.011"},
                                            {"3", "sell", "8300", "0.003"}};
        OrderServiceInterface orderService = setupTestOrderService(orders);

        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 1);

        Stack<NewLimitOrderSingle> openSellOrders = trader.getOpenSellOrders();
        assert(openSellOrders.size() == 3);
        assert(openSellOrders.pop().getPrice().doubleValue() == 8100);
        assert(openSellOrders.pop().getPrice().doubleValue() == 8200);
        assert(openSellOrders.pop().getPrice().doubleValue() == 8300); 
    }




    /**
    * Run a test starting with no open orders and we end up with a series of buy/sell orders
    * as the price of BTC falls. In this case the prices of bid/asks are such that we don't
    * use them to set the buy/sell limits, we use the prices of the previous buy/sell orders.
    */
    private void runTestForSeriesOfBuysUsingPricesOfPreviousOrdersToDriveNewLimits()
    {
        System.out.println("Running runTestForSeriesOfBuysUsingPricesOfPreviousOrdersToDriveNewLimits ..."); 

        double[] balances = new double[] {1000, 1000, 800, 640, 512, 409.6, 348.16, 295.936};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {13000, 11702, 11702, 11582, 11465, 11291, 11232, 11174};
        double[] asks = new double[] {13001, 11703, 11703, 11583, 11466, 11292, 11233, 11175};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

       
        // Iteration 1 
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() == null);



        // Iteration 2
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01709);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11700);



        // Iteration 3
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 1);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01709);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11761);

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01374);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11640);

       
 
        // Iteration 4
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 2);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01374);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11700); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.0111);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11522);


        
        // Iteration 5
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 3);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.0111);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11582); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00902);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11347);



        // Iteration 6
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 4);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.00902);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11462); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00544);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11288);


        
        // Iteration 7
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 5);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.00544);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11346); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00465);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11230);



        // Iteration 8
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 6);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.00465);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11288); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00399);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11116);
    }




    /**
    * Run a test starting with no open orders and we end up with a series of buy/sell orders
    * as the price of BTC falls. In this case the prices of bid/asks are such that we do
    * use them to set the buy/sell limits.
    */
    private void runTestForSeriesOfBuysUsingMarketPricesToDriveNewLimits()
    {
        System.out.println("Running runTestForSeriesOfBuysUsingMarketPricesToDriveNewLimits ..."); 

        double[] balances = new double[] {1000, 1000, 800, 640, 512, 409.6, 348.16, 295.936};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {13000, 11702, 11000, 10850, 10676, 10600, 10525, 10400};
        double[] asks = new double[] {13001, 11703, 11001, 10851, 10677, 10601, 10526, 10401};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

       
        // Iteration 1 
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() == null);



        // Iteration 2
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01709);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11700);



        // Iteration 3
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 1);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01709);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11761);

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01454);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10998);

      
 
        // Iteration 4
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 2);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01454);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11055); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01179);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10848);


        
        // Iteration 5
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 3);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01179);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 10904); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00959);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10674);



        // Iteration 6
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 4);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.00959);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 10783); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00579);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10598);


        
        // Iteration 7
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 5);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.00579);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 10653); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00496);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10523);



        // Iteration 8
        ((TestOrderService)orderService).fulfillLastOrder("buy");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 6);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.00496);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 10578); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.00426);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 10398);
    }




    /**
    * Run a test to explicitly check our first buy kicks in after set percentage drop and not before.
    */
    private void runTestForInitialBuyAfterBasePercentageDropFromHighestPriceSeen()
    {
        System.out.println("Running runTestForInitialBuyAfterBasePercentageDropFromHighestPriceSeen  ..."); 

        double[] balances = new double[] {1000, 1000, 1000};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {13000, 12936, 12935};
        double[] asks = new double[] {13001, 12937, 12936};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

       
        // Iteration 1 
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() == null);



        // Iteration 2
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);
        assert(trader.getCurrentBuyOrder() == null);



        // Iteration 3
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 0);

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01546);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 12933);
    }





   /**
    * Run a test starting with no open orders and we end up with a series of buy/sell orders
    * as the price of BTC falls. But at some point BTC rises and one of our sells go through
    * and we have to make the appropriate new buy order.
    */
    private void runTestForSeriesOfBuysThenASellUsingPricesOfPreviousOrdersToDriveNewLimits()
    {
        System.out.println("Running runTestForSeriesOfBuysThenASellUsingPricesOfPreviousOrdersToDriveNewLimits ..."); 

        double[] balances = new double[] {1000, 1000, 800, 640, 900};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {13000, 11702, 11702, 11582, 11701};
        double[] asks = new double[] {13001, 11703, 11703, 11583, 11702};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

       
        // Iterations 1-4...keep the last buy order in
        for (int x = 0; x < 4; ++ x)
        {
            runTestLoop(trader, 1);
            if (x < 3)
            {
                ((TestOrderService)orderService).fulfillLastOrder("buy");
            }
        }

        assert(trader.getOpenSellOrders().size() == 2);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01374);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11700); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.0111);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11522);


        // Iteration 5
        ((TestOrderService)orderService).fulfillLastOrder("sell");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 1);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01709);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11761);

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01546);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11640);
    }




    /**
    * Run a test starting with no open orders and we end up with a series of buy/sell orders
    * as the price of BTC falls. But at some point BTC rises and one of our sells go through
    * and we have to make the appropriate new buy order. This time the bid determines the 
    * new buy order price.
    */
    private void runTestForSeriesOfBuysThenASellUsingMarketPricesToDriveNewLimits()
    {
        System.out.println("Running runTestForSeriesOfBuysThenASellUsingMarketPricesToDriveNewLimits ..."); 

        double[] balances = new double[] {1000, 1000, 800, 640, 900};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {13000, 11702, 11600, 11550, 11584};
        double[] asks = new double[] {13001, 11703, 11601, 11551, 11585};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

       
        // Iterations 1-4...keep the last buy order in
        for (int x = 0; x < 4; ++ x)
        {
            runTestLoop(trader, 1);
            if (x < 3)
            {
                ((TestOrderService)orderService).fulfillLastOrder("buy");
            }
        }

        assert(trader.getOpenSellOrders().size() == 2);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01379);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11658); 

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01114);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11480);


        // Iteration 5
        ((TestOrderService)orderService).fulfillLastOrder("sell");
        runTestLoop(trader, 1);
        assert(trader.getOpenSellOrders().size() == 1);
        assert(trader.getOpenSellOrders().peek().getSize().doubleValue() == 0.01709);
        assert(trader.getOpenSellOrders().peek().getPrice().doubleValue() == 11761);

        assert(trader.getCurrentBuyOrder() != null);
        assert(trader.getCurrentBuyOrder().getSize().doubleValue() == 0.01554);
        assert(trader.getCurrentBuyOrder().getPrice().doubleValue() == 11582);
    }




    /**
    * Verify we take min BTC amount when placing an order.
    */
    private void runTestForNotEnoughBitcoinsInOrder()
    {
        System.out.println("Running runTestForNotEnoughBitcoinsInOrder ..."); 

        double[] balances = new double[] {150, 150, 150};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {35000, 34999, 32000};
        double[] asks = new double[] {35001, 35000, 32001};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 3);
        assert(trader.getCurrentBuyOrder() == null);
    }




    /**
    * Verify we take min $ amount when placing an order.
    */
    private void runTestForNotEnoughCash()
    {
        System.out.println("Running runTestForNotEnoughCash ..."); 

        double[] balances = new double[] {10, 10, 10};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {1000,  999, 870};
        double[] asks = new double[] {1001, 1000, 871};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 3);
        assert(trader.getCurrentBuyOrder() == null);
    }





    private void runTestForCancelingBuyAfterRunUp()
    {
        System.out.println("Running runTestForCancelingBuyAfterRunUp ..."); 

        double[] balances = new double[] {1000, 1000, 1000};
        AccountServiceInterface accountService = setupTestAccountService(balances);

        double[] bids = new double[] {8700, 8910};
        double[] asks = new double[] {8701, 8911};
        MarketDataServiceInterface marketDataService = setupTestMarketDataService(bids, asks);
        
        OrderServiceInterface orderService = setupTestOrderService(new String[][]{{"1", "buy", "8700", "2"}});
        Trader trader = new Trader(mTraderWindow, accountService, marketDataService, orderService);

        runTestLoop(trader, 3);
        assert(trader.getCurrentBuyOrder() == null);
    }/*





    private static void runTestForCancelingBuyAfterRunUp()
    {
        setupTest();
        setupTestAccountService(new double[] {});
        setupMarketDataService(new double[] {8700, 8910}, new double[] {8701, 8961});
        
        ArrayList<Order> orders = new ArrayList<Order>();
        Order order1 = new Order();
        order1.setSide("buy");
        order1.setSize("2");
        order1.setPrice("8700");
        order1.setId("1");
        orders.add(order1);
        mOrderService = new TestOrderService(orders);

        runTestLoop(1);
        assert(mCurrentBuyOrder != null);


        mCurrentBuyOrder = new NewLimitOrderSingle(order1);
        runTestLoop(1);
        assert(mCurrentBuyOrder == null);
    }
*/




    /**
    * Seed a test account service to return the given list of balances;
    */
    private AccountServiceInterface setupTestAccountService(double[] balances)
    {
        List<BigDecimal> balanceList = new ArrayList<BigDecimal>();
        for (int x = 0; x < balances.length; ++x)
        {
            balanceList.add(new BigDecimal(balances[x]));
        }
        return new TestAccountService(balanceList);
    }

 
    /**
    * Seed a test market data service to reutrn the given lists of bids/asks.
    */   
    private MarketDataServiceInterface setupTestMarketDataService(double[] bids, double[] asks)
    {
        List<OrderItem> asksList = new ArrayList<OrderItem>();
        List<OrderItem> bidsList = new ArrayList<OrderItem>();

        for (int x = 0; x < bids.length; ++x)
        {
            OrderItem bid = new OrderItem();
            bid.setPrice(new BigDecimal(bids[x]));
            bidsList.add(bid);
        }

        for (int x = 0; x < asks.length; ++x)
        {
            OrderItem ask = new OrderItem();
            ask.setPrice(new BigDecimal(asks[x]));
            asksList.add(ask);
        }

        return new TestMarketDataService(bidsList, asksList);
    }


    /**
    *  Seed a test order service to return the given orders.
    */
    private OrderServiceInterface setupTestOrderService(String[][] orders)
    {
        List<Order> ordersList = new ArrayList<Order>();
        for (int x = 0; x < orders.length; ++x)
        {
            Order order = new Order();
            order.setId(orders[x][0]);
            order.setSide(orders[x][1]);
            order.setPrice(orders[x][2]);
            order.setSize(orders[x][3]);
            ordersList.add(order);
        }
        return new TestOrderService(ordersList);
    }
}

