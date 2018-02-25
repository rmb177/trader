import com.coinbase.exchange.api.accounts.Account;
import com.coinbase.exchange.api.accounts.AccountService;
import com.coinbase.exchange.api.accounts.AccountServiceInterface;
import com.coinbase.exchange.api.entity.NewLimitOrderSingle;
import com.coinbase.exchange.api.exchange.GdaxExchange;
import com.coinbase.exchange.api.exchange.GdaxExchangeImpl;
import com.coinbase.exchange.api.exchange.Signature;
import com.coinbase.exchange.api.marketdata.MarketData;
import com.coinbase.exchange.api.marketdata.MarketDataService;
import com.coinbase.exchange.api.marketdata.MarketDataServiceInterface;
import com.coinbase.exchange.api.orders.Order;
import com.coinbase.exchange.api.orders.OrderService;
import com.coinbase.exchange.api.orders.OrderServiceInterface;

import org.springframework.web.client.RestTemplate;

import ui.TraderWindow;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JOptionPane;




public class Trader implements TraderWindow.CancelBuyOrderListener
{
    private final static boolean TESTING = true;

    private final static double MIN_USD = 10.00;
    private final static double MIN_BTC = 0.001;
    private final static double PERCENT_OF_BALANCE_FOR_PURCHASE = 0.1;
    private static boolean mDone;
    
    private TraderWindow mTraderWindow;

    private GdaxExchangeImpl mExchange;
    private AccountServiceInterface mAccountService;
    private MarketDataServiceInterface mMarketDataService;
    private OrderServiceInterface mOrderService;

    private String mAccountNumber;
    private BigDecimal mAvailableBalance;
    private BigDecimal mHighestBidSeen;
    private BigDecimal mCurrentBid;
    private BigDecimal mCurrentAsk;
    private NewLimitOrderSingle mCurrentBuyOrder = null;
    private Stack<NewLimitOrderSingle> mOpenSellOrders = new Stack<NewLimitOrderSingle>();

    private static String mCurrentBuyOrderId = null;
    private NewLimitOrderSingle mLastFulfilledSell = null;

   
    private static double[] targetBuyPercentages =  {0.0050, 0.0100, 0.02};
    private static double[] targetSellPercentages = {0.0050, 0.0050, 0.01};


    private static final double MAX_HIGH_BID_REACHED_BEFORE_CANCELING_LONE_BUY_ORDER = 0.01; 



    public static void main(String[] args)
    {
        javax.swing.SwingUtilities.invokeLater(new Runnable() 
        {
            public void run() 
            {
                if (!TESTING)
                {
                    TraderWindow traderWindow = new TraderWindow();

                    Trader trader = new Trader(traderWindow);
                    traderWindow.setCancelBuyOrderListener(trader);
                    trader.addShutdownHookToWriteExistingSellOrdersOnProgramExit();
                    trader.readInSellOrdrersFromLastShutdown();

                    JFrame mainFrame = new JFrame("Trader");
                    mainFrame.getContentPane().add(traderWindow);

                    mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    mainFrame.pack();
                    mainFrame.setVisible(true);

                    trader.getCredentialsAndInitializeServices(mainFrame);
                    ProcessThread t = new ProcessThread(trader, traderWindow);
                    t.start();
                }   
                else
                {
                    TraderTest test = new TraderTest();
                    test.runTests();
                }
            }
        }); 
    }



    public Trader(TraderWindow traderWindow)
    {
        mTraderWindow = traderWindow;
    }


    /**
    * Constructor for generating test instance.
    */
    public Trader(TraderWindow traderWindow,
                  AccountServiceInterface accountService,
                  MarketDataServiceInterface marketDataService,
                  OrderServiceInterface orderService)
    {
        mTraderWindow = traderWindow;
        mAccountService = accountService;
        mMarketDataService = marketDataService;
        mOrderService = orderService;
   }


    // Public access functions for testing
    public NewLimitOrderSingle getCurrentBuyOrder() { return mCurrentBuyOrder; }
    public Stack<NewLimitOrderSingle> getOpenSellOrders() { return mOpenSellOrders; }



    /**
    * When the program shuts down we write out existing sell orders so we
    * can read them back in next time the program runs and know whether or 
    * not they were fulfilled.
    */
    private void addShutdownHookToWriteExistingSellOrdersOnProgramExit()
    {
        if (!TESTING)
        {
            Runtime.getRuntime().addShutdownHook(new Thread() 
            {
                public void run()
                {
                    try
                    {
                        int numItemsOnStack = mOpenSellOrders.size();
                        PrintWriter writer = new PrintWriter("prev_sell_orders.txt", "UTF-8");
                        for (int x = 0; x < numItemsOnStack; ++x)
                        {
                            NewLimitOrderSingle order = mOpenSellOrders.pop();
                            writer.println(order.getSize() + ":" + order.getPrice());
                        }
                        writer.close();
                    }
                    catch (Exception e)
                    {
                        // Intentionally empty
                    }
                }
            });
        }
    }




    /**
    * Read in sell orders from the last time we shut down.
    */
    private void readInSellOrdrersFromLastShutdown()
    {
        try
        {
            URI uri = getClass().getResource("prev_sell_orders.txt").toURI();
            List<String> lines = Files.readAllLines(Paths.get(uri), Charset.defaultCharset());

            // We wrote out from stack top to bottom so when we read in need to reverse
            // to push back on in the right order.
            Collections.reverse(lines);
            for (String line : lines)
            {
                if (line.contains(":"))
                {
                    NewLimitOrderSingle order = new NewLimitOrderSingle();
                    String tokens[] = line.split(":");
                    order.setSize(new BigDecimal(tokens[0]));
                    order.setPrice(new BigDecimal(tokens[1]));
                    order.setSide("sell");
                    mOpenSellOrders.push(order);
                }
            }
        }
        catch (Exception e)
        {
            // Intentionally empty
        }
    }


   
    /**
    * Prompt for login information and initiate exchange object.
    */ 
    private void getCredentialsAndInitializeServices(JFrame frame)
    {
        mAccountNumber = (String)JOptionPane.showInputDialog(frame, "Account Number", "", JOptionPane.PLAIN_MESSAGE);
        String key = (String)JOptionPane.showInputDialog(frame, "Public Key", "", JOptionPane.PLAIN_MESSAGE);
        String passPhrase = (String)JOptionPane.showInputDialog(frame, "Passphrase", "", JOptionPane.PLAIN_MESSAGE);
        String secret = (String)JOptionPane.showInputDialog(frame, "Secret", "", JOptionPane.PLAIN_MESSAGE);
        Signature sig = new Signature(secret);

        mExchange = new GdaxExchangeImpl(key, passPhrase, "https://api.gdax.com/", sig, new RestTemplate());
        mAccountService = new AccountService(mExchange);
        mMarketDataService = new MarketDataService(mExchange);
        mOrderService = new OrderService(mExchange);
    }
    


    /**
    * The main thread to process market informtion and place buy/sell orders.
    */
    private static class ProcessThread implements Runnable
    {
        private Thread t;
        private Trader mTrader;
        private TraderWindow mTraderWindow;


        public ProcessThread(Trader trader, TraderWindow traderWindow)
        {
            mTrader = trader;
            mTraderWindow = traderWindow;
        }

        public void run()
        {
            while (!mDone)
            {
                try
                {
                    mTrader.synchOurTimeWithServer();
                    mTrader.checkOrderStatus();
                    Thread.sleep(mTraderWindow.getNextPollingInterval());
                }
                catch (Exception e)
                {
                    mTraderWindow.setErrorText(e.getMessage());
                    for (int x = 0; x < e.getStackTrace().length; ++x)
                    {
                        System.out.println(e.getStackTrace()[x].toString());
                    }
                }
            }
        }

        void start()
        {
            if (t == null)
            {
                t = new Thread(this);
                t.start();
            }
        }
    }


    /**
    * Main processing loop
    * Iterates through orders to get current state, determine if any orders have processed since last checked
    * and initiate new orders as needed.
    */
    public void checkOrderStatus()
        throws Exception
    {
        getMarketData();

        List<Order> openOrders = mOrderService.getOpenOrders();
        checkSellOrders(openOrders);
        checkBuyOrders(openOrders);

        mTraderWindow.setMarketData(mAvailableBalance, mHighestBidSeen, mCurrentBid, mCurrentAsk);
        mTraderWindow.setLastUpdatedText(new Date());
    } 



    /**
    * Get all the open sell orders. If we have less orders than are what are on the stack
    * pop off the top orders until they are the same.
    */
    private void checkSellOrders(List<Order> openOrders)
        throws IOException
    {
        List<Order> sellOrders = openOrders.stream().filter(order -> !order.getSide().equals("buy")).collect(Collectors.toList());
        Collections.sort(sellOrders);

        // We should only have an empty stack and open orders when the program starts.
        if (sellOrders.size() > 0 && mOpenSellOrders.size() == 0)
        {
            for (Order order : sellOrders)
            {
                mOpenSellOrders.push(new NewLimitOrderSingle(order));
            }
        }
        else
        {
            while (sellOrders.size() < mOpenSellOrders.size())
            {
                mLastFulfilledSell = mOpenSellOrders.pop();
                
                Date date = new Date();
                writeOrderToDisk(mLastFulfilledSell, date);
                mTraderWindow.writeOrderToScreen(mLastFulfilledSell, date);
            }
        }
        mTraderWindow.displayCurrentSellOrders(mOpenSellOrders);
    }

    /**
    * Determine if we had a buy order that went through and/or enter a new
    * buy order.
    */
    private void checkBuyOrders(List<Order> openOrders)
        throws IOException
    {
        List<Order> buyOrders = openOrders.stream().filter(order -> order.getSide().equals("buy")).collect(Collectors.toList());

        if (sellOrderFullfiledAndNeedToCancelLowerBuyOrder(buyOrders))
        {
            clearBuyOrders(buyOrders);
        }

        // Get available balance after potentially canceling order that way we have that $ available.
        getAvailableBalance();


        if (buyOrders.size() > 1)
        {
            mTraderWindow.setErrorText("You have more than one buy order open!!");
            clearBuyOrders(buyOrders);
        }
        else if (buyOrders.size() == 1)
        {
            NewLimitOrderSingle tempOrder = new NewLimitOrderSingle(buyOrders.get(0));
            if (mCurrentBuyOrder != null &&
                    (mCurrentBuyOrder.getSize().doubleValue() != tempOrder.getSize().doubleValue() ||
                     mCurrentBuyOrder.getPrice().doubleValue() != tempOrder.getPrice().doubleValue()))
            {
                mTraderWindow.setErrorText("Your current buy order does not match the one you have in memory!!");
                clearBuyOrders(buyOrders);
            }
            else
            {
                if (mCurrentAsk != null &&
                    mCurrentBuyOrder != null &&
                    mOpenSellOrders.size() == 0 &&
                        mCurrentAsk.doubleValue() >= 
                        (mCurrentBuyOrder.getPrice().doubleValue() + (mCurrentBuyOrder.getPrice().doubleValue() * MAX_HIGH_BID_REACHED_BEFORE_CANCELING_LONE_BUY_ORDER)))
                {
                    clearBuyOrders(buyOrders);
                }
                else
                {
                    mCurrentBuyOrderId = buyOrders.get(0).getId();
                    mCurrentBuyOrder = new NewLimitOrderSingle(buyOrders.get(0)); 
                    mTraderWindow.displayCurrentBuyOrder(mCurrentBuyOrder);
                }
            }
        }
        else if (buyOrders.size() == 0)
        {
            // We had a buy order and now don't so it went through. Write it out to disk and create the next order
            if (mCurrentBuyOrder != null)
            {
                Date date = new Date();
                writeOrderToDisk(mCurrentBuyOrder, date);
                mTraderWindow.writeOrderToScreen(mCurrentBuyOrder, date);

                double lastBuyPrice = mCurrentBuyOrder.getPrice().doubleValue();

                // Create sell order
                double sellTargetPercentIncrease = targetSellPercentages[mOpenSellOrders.size() % 3]; 
                double sellTargetPrice = lastBuyPrice + (lastBuyPrice * sellTargetPercentIncrease);
                long sellLimitPrice = Math.round((Math.max(sellTargetPrice, mCurrentAsk.doubleValue()) + 2));

                Order returnedSellOrder = createOrder("sell", mCurrentBuyOrder.getSize().doubleValue(), (int)sellLimitPrice); 
                if (returnedSellOrder != null && !returnedSellOrder.getStatus().equals("rejected"))
                {
                    mOpenSellOrders.push(new NewLimitOrderSingle(returnedSellOrder));
                    mTraderWindow.displayCurrentSellOrders(mOpenSellOrders);
                
                    double buyTargetPrice = lastBuyPrice - (lastBuyPrice * targetBuyPercentages[mOpenSellOrders.size() % 3]);
                    long buyLimitPrice = Math.round((Math.min(buyTargetPrice, mCurrentBid.doubleValue()) - 2));
                    double dollarAmount = mAvailableBalance.doubleValue() * PERCENT_OF_BALANCE_FOR_PURCHASE;
                    double buyOrderSize = dollarAmount / buyLimitPrice;

                    Order returnedBuyOrder = createOrder("buy", buyOrderSize, (int)buyLimitPrice); 
                    if (returnedBuyOrder != null && !returnedBuyOrder.getStatus().equals("rejected"))
                    {
                        mCurrentBuyOrderId = returnedBuyOrder.getId();
                        mCurrentBuyOrder = new NewLimitOrderSingle(returnedBuyOrder);
                        mTraderWindow.displayCurrentBuyOrder(mCurrentBuyOrder);
                    }
                    else
                    {
                        mTraderWindow.setErrorText("Something went wrong with submitting new buy order!!");
                        mDone = true;
                    }
                }
                else
                {
                    mTraderWindow.setErrorText("Something went wrong with submitting new sell order!!");
                    mDone = true;
                }
            }
            else
            {
                long buyLimitPrice = -1;

                // Create new order off sell order for previous buy
                if (mOpenSellOrders.size() > 0 && mCurrentBid != null)
                {
                    // UPDATE ME AT SOME POINT SHOULD WE WRITE OUT LAST FULFILLED SELL AT SHUTDOWN 
                    double valueToUse = mLastFulfilledSell == null ? mCurrentBid.doubleValue() : mLastFulfilledSell.getPrice().doubleValue();
                    double lastBuyPrice = valueToUse * (1 - targetSellPercentages[mOpenSellOrders.size() % 3]);
                    buyLimitPrice = Math.round((Math.min(lastBuyPrice, mCurrentBid.doubleValue()) - 2));
                }
                else if (priceHasFallenEnoughFromHighestBidSeen())
                {
                    buyLimitPrice = mCurrentBid.longValue() - 2;
                }

                if (buyLimitPrice != -1)
                {
                    double dollarAmount = mAvailableBalance.doubleValue() * PERCENT_OF_BALANCE_FOR_PURCHASE;
                    double buyOrderSize = dollarAmount / buyLimitPrice;

                    Order returnedBuyOrder = createOrder("buy", buyOrderSize, buyLimitPrice); 
                    if (returnedBuyOrder != null && !returnedBuyOrder.getStatus().equals("rejected"))
                    {
                        mCurrentBuyOrderId = returnedBuyOrder.getId();
                        mCurrentBuyOrder = new NewLimitOrderSingle(returnedBuyOrder);
                        mTraderWindow.displayCurrentBuyOrder(mCurrentBuyOrder);
                    }
                }
            }
        }
        mLastFulfilledSell = null;
    }


    /**
    * Return true if a sell order has been fulfilled and we need to cancel an outstanding
    * buy order at a lower level.
    */
    private boolean sellOrderFullfiledAndNeedToCancelLowerBuyOrder(List<Order> buyOrders)
    {
        return mLastFulfilledSell != null && mCurrentBuyOrder != null && buyOrders.size() == 1;
    }


    /**
    * Return true if the price of BTC has fallen enough from the highest bid we've seen
    * so far to make an initial purchase (first purchase of session or previous sell
    * orders have all been filled.)
    */
    private boolean priceHasFallenEnoughFromHighestBidSeen()
    {
        if (mHighestBidSeen != null && mCurrentBid != null)
        {
            double percentOffHighBid = (mHighestBidSeen.doubleValue() - mCurrentBid.doubleValue()) / mHighestBidSeen.doubleValue();
            return percentOffHighBid >= targetBuyPercentages[0];
        }
        return false;
    }



    /**
    * Get the current ask/bid from the exchange and verify highest bid we've seen.
    */
    private void getMarketData()
    {
        MarketData marketData = mMarketDataService.getMarketDataOrderBook("BTC-USD", "");
         
        if (marketData.getBids().size() > 0)
        {
            mCurrentBid = marketData.getBids().get(0).getPrice(); 
            if (null == mHighestBidSeen || mCurrentBid.compareTo(mHighestBidSeen) == 1)
            {
                mHighestBidSeen = mCurrentBid;
            }
        }

        if (marketData.getAsks().size() > 0)
        {
            mCurrentAsk = marketData.getAsks().get(0).getPrice();
        }
    }


    /**
    * Retrieve the available balance from my account.
    */
    private void getAvailableBalance()
    {
        mAvailableBalance = mAccountService.getAccount(mAccountNumber).getAvailable();
    }



    private Order createOrder(String side, double orderSize, long limitPrice)
    {
        if (side.equals("buy") &&
            (orderSize < MIN_BTC || orderSize * limitPrice < MIN_USD))
        {
            return null;
        }
        else
        {
            DecimalFormat df = new DecimalFormat("#.#####");
            df.setRoundingMode(RoundingMode.DOWN);

            NewLimitOrderSingle newOrder = new NewLimitOrderSingle();
            newOrder.setSide(side);
            newOrder.setProduct_id("BTC-USD");
            newOrder.setType("limit");
            newOrder.setPost_only(true);
            newOrder.setSize(new BigDecimal(df.format(orderSize)));
            newOrder.setPrice(new BigDecimal(limitPrice));

            return mOrderService.createOrder(newOrder);
        }
    }


    
    
    /**
    * Clear out all of our current buy orders. Used if:
    *   * something goes wrong
    *   * we had a sell order that went through and we need to cancel outstanding buy order at lower level
    *   * we have a single buy order, no sell orders, and the BTC price has gone up too far from our buy order
    */
    public void clearBuyOrders(List<Order> buyOrders)
    {
        for (Order order : buyOrders)
        {
            mOrderService.cancelOrder(order.getId());
        }
        buyOrders.clear();

        mOrderService.cancelOrder(mCurrentBuyOrderId);
        mCurrentBuyOrderId = null;
        mCurrentBuyOrder = null;
        mTraderWindow.displayCurrentBuyOrder(mCurrentBuyOrder);
    }


    public void buyOrderCanceled()
    {
        mOrderService.cancelOrder(mCurrentBuyOrderId);
        mCurrentBuyOrderId = null;
        mCurrentBuyOrder = null;
        mTraderWindow.displayCurrentBuyOrder(mCurrentBuyOrder);
    }
    



    /**
    * Write the given order out to the filled orders directory.
    * as an empty file with format yyyymmddHHmmss_buy|sell_numcoins_price
    */   
    private void writeOrderToDisk(NewLimitOrderSingle order, Date date)
        throws IOException
    {
        if (!TESTING)
        {
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            String fileName = "filled_orders\\" + dateFormat.format(date) + "_" + order.getSide() + "_1.txt";
            File f = new File(fileName);
            int suffix = 1;
            while (f.exists())
            {
                suffix += 1;
                fileName = "filled_orders\\" + dateFormat.format(date) + "_" + order.getSide() + "_" + suffix + ".txt";
                f = new File(fileName);
            }

            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            writer.println("size = " + order.getSize());
            writer.println("price = " + order.getPrice());
            writer.close();
        }
    }

   
    /**
    * Make sure our time synchs with GDAX server.
    */ 
    private void synchOurTimeWithServer()
        throws Exception
    {
        URL timeUrl = new URL("https://api.gdax.com/time");
        URLConnection conn = timeUrl.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine = in.readLine();
        in.close(); 

        String timestamp = inputLine.substring(inputLine.indexOf("epoch") + 7, inputLine.lastIndexOf("."));
        mExchange.setTimestamp(timestamp);
    }
}
