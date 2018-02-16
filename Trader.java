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
import com.coinbase.exchange.api.marketdata.OrderItem;
import com.coinbase.exchange.api.orders.Order;
import com.coinbase.exchange.api.orders.OrderService;
import com.coinbase.exchange.api.orders.OrderServiceInterface;

import org.springframework.web.client.RestTemplate;

import test.TestAccountService;
import test.TestMarketDataService;
import test.TestOrderService;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Scanner; 
import java.util.Stack;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;

public class Trader
{
    private final static boolean TESTING = false;
    private static int TEST_ITERATIONS = 0;

    private final static double MIN_USD = 10.00;
    private final static double MIN_BTC = 0.001;
 
   
    private static AccountServiceInterface mAccountService;
    private static MarketDataServiceInterface mMarketDataService;
    private static OrderServiceInterface mOrderService;

    private static JFrame mMainFrame = new JFrame("Trader");
    private static JLabel mErrorLabel = new JLabel("");
    private static JLabel mAvailableCashLabel = new JLabel();
    private static JLabel mHighestBidLabel = new JLabel();
    private static JLabel mBidLabel = new JLabel();
    private static JLabel mAskLabel = new JLabel();
    private static JTextField mPollingField = new JTextField("1");
    private static JLabel mBuyOrderIdLabel = new JLabel();
    private static JTable mOpenBuyTable;
    private static BuyTableModel mBuyTableModel;
    private static JTable mOpenSellsTable; 
    private static SellTableModel mSellTableModel;
    private static JTextArea mFilledOrders = new JTextArea();
    private static JLabel mLastUpdatedLabel = new JLabel();

    private static Account mUSDAccount;
    private static BigDecimal mHighestBidSeen;
    private static BigDecimal mCurrentBid;
    private static BigDecimal mCurrentAsk;
    private static NewLimitOrderSingle mCurrentBuyOrder = null;
    private static Stack<NewLimitOrderSingle> mOpenSellOrders = new Stack<NewLimitOrderSingle>();


    private static boolean mSellOrderFilled = false;
    private static boolean mDone;

    
    private static void createAndShowGUI()
    {
        mMainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Error label
        JPanel errorPanel = new JPanel();
        errorPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        errorPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mErrorLabel.setForeground(Color.RED);
        errorPanel.add(mErrorLabel);


        // Exchange panel
        GridLayout gridLayout = new GridLayout(0, 2, 5, 10);
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(gridLayout);
        dataPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
       
        dataPanel.add(new JLabel("$ Available:"));
        dataPanel.add(mAvailableCashLabel); 
        dataPanel.add(new JLabel("Highest Bid:"));
        dataPanel.add(mHighestBidLabel);
        dataPanel.add(new JLabel("Current Bid:"));
        dataPanel.add(mBidLabel);
        dataPanel.add(new JLabel("Current Ask:"));
        dataPanel.add(mAskLabel);
        dataPanel.add(new JLabel("Polling Frequency (mins):"));
        dataPanel.add(mPollingField);
        dataPanel.add(new JLabel("Buy order ID:"));
        dataPanel.add(mBuyOrderIdLabel);

        
        // Outstanding Orders
        JPanel outstandingOrdersPanel = new JPanel();
        outstandingOrdersPanel.setLayout(new BoxLayout(outstandingOrdersPanel, BoxLayout.Y_AXIS));
        outstandingOrdersPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Outstanding Buy Order
        mBuyTableModel = new BuyTableModel(); 
        mOpenBuyTable = new JTable(mBuyTableModel); 
        mOpenBuyTable.setPreferredScrollableViewportSize(new Dimension(200, 18));
        mOpenBuyTable.setFillsViewportHeight(true);
        mOpenBuyTable.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(mOpenBuyTable);

        outstandingOrdersPanel.add(new JLabel("Outstanding Buy Order"));
        outstandingOrdersPanel.add(scrollPane);
        outstandingOrdersPanel.add(Box.createRigidArea(new Dimension(0,40)));

        // Outstanding Sell Orders
        mSellTableModel = new SellTableModel();
        mOpenSellsTable = new JTable(mSellTableModel); 
        mOpenSellsTable.setPreferredScrollableViewportSize(new Dimension(200, 100));
        mOpenSellsTable.setFillsViewportHeight(true);
        mOpenSellsTable.setEnabled(false);
        scrollPane = new JScrollPane(mOpenSellsTable);

        outstandingOrdersPanel.add(new JLabel("Outstanding Sell Orders"));
        outstandingOrdersPanel.add(scrollPane);


        // Filled orders and last updated status
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        mFilledOrders.setLineWrap(true);
        mFilledOrders.setWrapStyleWord(true);
        mFilledOrders.setEditable(false);
        JScrollPane areaScrollPane = new JScrollPane(mFilledOrders);
        areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        areaScrollPane.setPreferredSize(new Dimension(850, 250));
        areaScrollPane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), areaScrollPane.getBorder()));

        JPanel updatePanel = new JPanel();
        updatePanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        updatePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        updatePanel.add(mLastUpdatedLabel);

        bottomPanel.add(areaScrollPane);
        bottomPanel.add(updatePanel);

        BorderLayout borderLayout = new BorderLayout(20, 20);
        mMainFrame.getContentPane().setLayout(borderLayout);
        mMainFrame.getContentPane().add(errorPanel, BorderLayout.PAGE_START);
        mMainFrame.getContentPane().add(dataPanel, BorderLayout.LINE_START);
        mMainFrame.getContentPane().add(outstandingOrdersPanel, BorderLayout.CENTER);
        mMainFrame.getContentPane().add(bottomPanel, BorderLayout.PAGE_END);
        mMainFrame.pack();
        mMainFrame.setVisible(true);
    }
 

    private static void getCredentialsAndInitializeServices(JFrame frame)
    {
        String key = (String)JOptionPane.showInputDialog(frame, "Public Key", "", JOptionPane.PLAIN_MESSAGE);
        String passPhrase = (String)JOptionPane.showInputDialog(frame, "Passphrase", "", JOptionPane.PLAIN_MESSAGE);
        String secret = (String)JOptionPane.showInputDialog(frame, "Secret", "", JOptionPane.PLAIN_MESSAGE);
        Signature sig = new Signature(secret);

        GdaxExchange exchange = new GdaxExchangeImpl(key, passPhrase, "https://api.gdax.com/", sig, new RestTemplate());
        mAccountService = new AccountService(exchange);
        mMarketDataService = new MarketDataService(exchange);
        mOrderService = new OrderService(exchange);
    }
    


    private static class RefreshExchangeInformation implements Runnable
    {
        private Thread t;

        public void run()
        {
            while (!mDone)
            {
                try
                {
                    checkOrderStatus();
                    Thread.sleep(Integer.parseInt(mPollingField.getText()) * 1000 * 60);
                }
                catch (Exception e)
                {
                    mErrorLabel.setText(e.getMessage());
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
    private static void checkOrderStatus()
        throws Exception
    {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();

        mUSDAccount = mAccountService.getAccount("0bbece32-37d9-4180-99a5-7b6381e5c114");
        mAvailableCashLabel.setText(currencyFormat.format(mUSDAccount.getAvailable())); 

        MarketData marketData = mMarketDataService.getMarketDataOrderBook("BTC-USD", "");
       
        if (marketData.getBids().size() > 0)
        {
            mCurrentBid = marketData.getBids().get(0).getPrice(); 
            mBidLabel.setText(currencyFormat.format(marketData.getBids().get(0).getPrice()));

            if (null == mHighestBidSeen || mCurrentBid.compareTo(mHighestBidSeen) == 1)
            {
                mHighestBidSeen = mCurrentBid;
                mHighestBidLabel.setText(currencyFormat.format(mHighestBidSeen));
            }
        }

        if (marketData.getAsks().size() > 0)
        {
            mCurrentAsk = marketData.getAsks().get(0).getPrice();
            mAskLabel.setText(currencyFormat.format(marketData.getAsks().get(0).getPrice()));
        }

        List<Order> openOrders = mOrderService.getOpenOrders();
        checkSellOrders(openOrders);
        checkBuyOrders(openOrders);
    
        mLastUpdatedLabel.setText("Last Update: " + new Date().toString());


        System.out.println("=========================================================");
        System.out.print("current buy order:  ");
        if (mCurrentBuyOrder != null)
        {
            System.out.println(mCurrentBuyOrder.toString());
        }
        else
        {
            System.out.println("null");
        }
        System.out.println("number sell orders:  " + mOpenSellOrders.size());
        System.out.println("=========================================================");
    } 



    /**
    * Get all the open sell orders. If we have less orders than are what are on the stack
    * pop off the top orders until they are the same.
    */
    private static void checkSellOrders(List<Order> openOrders)
        throws IOException
    {
        mSellOrderFilled = false;

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
                NewLimitOrderSingle order = mOpenSellOrders.pop();
                Date date = new Date();
                writeOrderToDisk(order, date);
                writeOrderToScreen(order, date);
                mSellOrderFilled = true;
            }
        }
        NewLimitOrderSingle[] tempSells = new NewLimitOrderSingle[mOpenSellOrders.size()];
        mOpenSellOrders.copyInto(tempSells);
        mSellTableModel.setData(tempSells);
    }

    /**
    * I should never have more than one buy order at a time. Also if I have
    * a buy order still open, it should match the one in memory if I've already
    * set it.
    *
    * If there are no open buy orders and we previously had one then it went through
    * and we need to record it.
    */
    private static void checkBuyOrders(List<Order> openOrders)
        throws IOException
    {
        List<Order> buyOrders = openOrders.stream().filter(order -> order.getSide().equals("buy")).collect(Collectors.toList());
        if (mSellOrderFilled)
        {
            clearBuyOrders(buyOrders);
        }
        else if (buyOrders.size() > 1)
        {
            mErrorLabel.setText("You have more than one buy order open!!");
            clearBuyOrders(buyOrders);
        }
        else if (buyOrders.size() == 1)
        {
            NewLimitOrderSingle tempOrder = new NewLimitOrderSingle(buyOrders.get(0));
            if (mCurrentBuyOrder != null &&
                    (mCurrentBuyOrder.getSize().doubleValue() != tempOrder.getSize().doubleValue() ||
                     mCurrentBuyOrder.getPrice().doubleValue() != tempOrder.getPrice().doubleValue()))
            {
                mErrorLabel.setText("Your current buy order does not match the one you have in memory!!");
                clearBuyOrders(buyOrders);
            }
            else
            {
                mCurrentBuyOrder = new NewLimitOrderSingle(buyOrders.get(0)); 
                mBuyTableModel.setData(mCurrentBuyOrder);
            }
        }
        else if (buyOrders.size() == 0)
        {
            // We had a buy order and now don't so it went through. Write it out to disk
            // and create the next order.
            if (mCurrentBuyOrder != null)
            {
                Date date = new Date();
                writeOrderToDisk(mCurrentBuyOrder, date);
                writeOrderToScreen(mCurrentBuyOrder, date);

                double lastBuyPrice = mCurrentBuyOrder.getPrice().doubleValue();

                // Create sell order
                double sellTargetPercentIncrease = (mOpenSellOrders.size() < 2) ? 0.01 : mOpenSellOrders.size() * 0.01;
                double sellTargetPrice = lastBuyPrice + (lastBuyPrice * sellTargetPercentIncrease);
                long sellLimitPrice = Math.round((Math.max(sellTargetPrice, mCurrentAsk.doubleValue()) + 10));

                Order returnedSellOrder = createOrder("sell", mCurrentBuyOrder.getSize().doubleValue(), (int)sellLimitPrice); 
                if (returnedSellOrder != null && !returnedSellOrder.getStatus().equals("rejected"))
                {
                    mOpenSellOrders.push(new NewLimitOrderSingle(returnedSellOrder));
                    NewLimitOrderSingle[] tempSells = new NewLimitOrderSingle[mOpenSellOrders.size()];
                    mOpenSellOrders.copyInto(tempSells);
                    mSellTableModel.setData(tempSells);
                    
                    double buyTargetPercentDecrease = (mOpenSellOrders.size() + 1) * 0.01;
                    double buyTargetPrice = lastBuyPrice - (lastBuyPrice * buyTargetPercentDecrease);

                    long buyLimitPrice = Math.round((Math.min(buyTargetPrice, mCurrentBid.doubleValue()) - 10));
                    double dollarAmount = mUSDAccount.getAvailable().doubleValue() * .2;
                    double buyOrderSize = dollarAmount / buyLimitPrice;

                    Order returnedBuyOrder = createOrder("buy", buyOrderSize, (int)buyLimitPrice); 
                    if (returnedBuyOrder != null && !returnedBuyOrder.getStatus().equals("rejected"))
                    {
                        mBuyOrderIdLabel.setText(returnedBuyOrder.getId());
                        mCurrentBuyOrder = new NewLimitOrderSingle(returnedBuyOrder);
                        mBuyTableModel.setData(mCurrentBuyOrder);
                    }
                    else
                    {
                        mErrorLabel.setText("Something went wrong with submitting new buy order!!");
                        mDone = true;
                    }
                }
                else
                {
                    mErrorLabel.setText("Something went wrong with submitting new sell order!!");
                    mDone = true;
                }
            }
            else
            {
                long buyLimitPrice = -1;

                // Create new order off sell order for previous buy
                if (mOpenSellOrders.size() > 0 && mCurrentBid != null)
                {
                    double previousBuyTargetPercentDecrease = mOpenSellOrders.size() * 0.01;
                    double newBuyTargetPercentDecrease = (mOpenSellOrders.size() + 1) * 0.01;
                    double lastBuyPrice = mOpenSellOrders.peek().getPrice().doubleValue() * (1 - previousBuyTargetPercentDecrease);
                    double buyTargetPrice = lastBuyPrice - (lastBuyPrice * newBuyTargetPercentDecrease);
                    buyLimitPrice = Math.round((Math.min(buyTargetPrice, mCurrentBid.doubleValue()) - 10));
                }
                else    // we don't have any open sell orders, start from scratch based on highest bid order we've currently seen or drop in price
                { 
                    if (mHighestBidSeen != null &&
                        mCurrentBid != null &&
                        ((mHighestBidSeen.doubleValue() - mCurrentBid.doubleValue()) / mHighestBidSeen.doubleValue()) > 0.01)
                    {
                        buyLimitPrice = mCurrentBid.longValue() - 10;
                    }
                }

                if (buyLimitPrice != -1)
                {
                    double dollarAmount = mUSDAccount.getAvailable().doubleValue() * .2;
                    double buyOrderSize = dollarAmount / buyLimitPrice;
                
                    Order returnedBuyOrder = createOrder("buy", buyOrderSize, buyLimitPrice); 
                    if (returnedBuyOrder != null && !returnedBuyOrder.getStatus().equals("rejected"))
                    {
                        mBuyOrderIdLabel.setText(returnedBuyOrder.getId());
                        mCurrentBuyOrder = new NewLimitOrderSingle(returnedBuyOrder);
                        mBuyTableModel.setData(mCurrentBuyOrder);
                    }
                }
            }
        }
    }


    private static Order createOrder(String side, double orderSize, long limitPrice)
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


    /**
    * Something screwed up so just clear out all buy orders and
    * start from scratch.
    */
    private static void clearBuyOrders(List<Order> buyOrders)
    {
        for (Order order : buyOrders)
        {
            mOrderService.cancelOrder(order.getId());
        }
        mCurrentBuyOrder = null;
        mBuyTableModel.setData(mCurrentBuyOrder);
    }



    /**
    * Write the given order out to the filled orders directory.
    * as an empty file with format yyyymmddHHmmss_buy|sell_numcoins_price
    */   
    private static void writeOrderToDisk(NewLimitOrderSingle order, Date date)
        throws IOException
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

    
    /**
    * Write the given order to the filled orders text area.
    */
    private static void writeOrderToScreen(NewLimitOrderSingle order, Date date)
    {
        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        String outputText = dateFormat.format(date) + "\t" + order.getSide() + "\t" + order.getSize() + "\t" + order.getPrice() + "\n";
        mFilledOrders.append(outputText); 
    } 



    public static void main(String[] args)
    {
        Runtime.getRuntime().addShutdownHook(new Thread() 
        {
            public void run()
            {
                try
                {
                    PrintWriter writer = new PrintWriter("prev_sell_orders.txt", "UTF-8");
                    for (int x = 0; x < mOpenSellOrders.size(); ++x)
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
            };
        });


        javax.swing.SwingUtilities.invokeLater(new Runnable() 
        {
            public void run() 
            {
                try
                {
                    URI uri = this.getClass().getResource("prev_sell_orders.txt").toURI();
                    List<String> lines = Files.readAllLines(Paths.get(uri), Charset.defaultCharset());
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

                createAndShowGUI();
                if (!TESTING)
                {
                    getCredentialsAndInitializeServices(mMainFrame);
                    RefreshExchangeInformation t = new RefreshExchangeInformation();
                    t.start();
                }   
                else
                {
                    runTests();
                }
            }
        }); 
    }




    private static class BuyTableModel extends AbstractTableModel
    {
        private NewLimitOrderSingle data;
        private final String[] columnNames = {"Coins", "Limit Price", "Total"};

        public void setData(NewLimitOrderSingle order)
        {
            data = order;
            
        }

        public int getRowCount()
        {
            return 1;
        }

        public int getColumnCount()
        {
            return 3;
        }


        public Object getValueAt(int row, int column)
        {
            if (data != null)
            {
                if (column == 0)
                {
                    return data.getSize().toString();
                }
                else if (column == 1)
                {
                    return data.getPrice().toString();
                }
                else
                {
                    return String.valueOf(data.getPrice().doubleValue() *  data.getSize().doubleValue());
                }
            }
            return "";
        }

        public String getColumnName(int index)
        {
            return columnNames[index];
        }        
    }




    private static class SellTableModel extends AbstractTableModel
    {
        private NewLimitOrderSingle[] data;
        private final String[] columnNames = {"Coins", "Limit Price", "Total"};

        public void setData(NewLimitOrderSingle[] orders)
        {
            data = orders;
            
        }

        public int getRowCount()
        {
            return data == null ? 0 : data.length;
        }

        public int getColumnCount()
        {
            return 3;
        }


        public Object getValueAt(int row, int column)
        {
            if (data != null && row < data.length)
            {
                if (column == 0)
                {
                    return data[row].getSize().toString();
                }
                else if (column == 1)
                {
                    return data[row].getPrice().toString();
                }
                else
                {
                    return String.valueOf(data[row].getPrice().doubleValue() *  data[row].getSize().doubleValue());
                }
            }
            return "";
        }

        public String getColumnName(int index)
        {
            return columnNames[index];
        }        
    }

     
    


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////////////////////
    private static void runTestLoop()
    {
        try
        {
            for (int x = 0; x < TEST_ITERATIONS; ++x)
            {
                checkOrderStatus();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }


    private static void runTests()
    {
        runTestForMultipleOpenBuysFromExchange();
        runTestForDifferentBuyOrderFromExchange();
        runTestForOneOpenBuyOrderFromExchange();
        runTestSortingSellOrdersAppropriatelyAtStartup();
        runTestForSeriesOfBuysUsingPricesOfPreviousOrdersToDriveNewLimits();
        runTestForSeriesOfBuysUsingMarketPricesToDriveNewLimits();
        runTestForInitialBuyAfterOnePercentDropFromHighestPriceSeen();
    }



    private static void runTestForMultipleOpenBuysFromExchange()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        mAccountService = new TestAccountService(null);
        mMarketDataService = new TestMarketDataService(new ArrayList<OrderItem>(), new ArrayList<OrderItem>());

        ArrayList<Order> orders = new ArrayList<Order>();
        Order order1 = new Order();
        Order order2 = new Order();
        order1.setId("1");
        order2.setId("2");
        order1.setSide("buy");
        order2.setSide("buy");
        orders.add(order1);
        orders.add(order2);

        mOrderService = new TestOrderService(orders);

        TEST_ITERATIONS = 1;
        runTestLoop();
        assert(mErrorLabel.getText().equals("You have more than one buy order open!!"));
        assert(mOrderService.getOpenOrders().size() == 0);
        assert(mCurrentBuyOrder == null);
    }

    private static void runTestForDifferentBuyOrderFromExchange()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        mAccountService = new TestAccountService(null);
        mMarketDataService = new TestMarketDataService(new ArrayList<OrderItem>(), new ArrayList<OrderItem>());

        mCurrentBuyOrder = new NewLimitOrderSingle();
        mCurrentBuyOrder.setSize(new BigDecimal(3));
        mCurrentBuyOrder.setPrice(new BigDecimal(2));

        ArrayList<Order> orders = new ArrayList<Order>();
        Order order1 = new Order();
        order1.setId("1");
        order1.setSide("buy");
        order1.setSize("2");
        order1.setPrice("3");
        orders.add(order1);

        mOrderService = new TestOrderService(orders);

        TEST_ITERATIONS = 1;
        runTestLoop();
        assert(mErrorLabel.getText().equals("Your current buy order does not match the one you have in memory!!"));
        assert(mOrderService.getOpenOrders().size() == 0);
        assert(mCurrentBuyOrder == null);
    }

    private static void runTestForOneOpenBuyOrderFromExchange()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        mAccountService = new TestAccountService(null);
        mMarketDataService = new TestMarketDataService(new ArrayList<OrderItem>(), new ArrayList<OrderItem>());

        ArrayList<Order> orders = new ArrayList<Order>();
        Order order1 = new Order();
        order1.setSide("buy");
        order1.setSize("2");
        order1.setPrice("3");
        orders.add(order1);
        mOrderService = new TestOrderService(orders);

        NewLimitOrderSingle limitOrder = new NewLimitOrderSingle(order1);

        TEST_ITERATIONS = 1;
        runTestLoop();
        assert(mOrderService.getOpenOrders().size() == 1);
        assert(mCurrentBuyOrder.getSize().equals(limitOrder.getSize()));
        assert(mCurrentBuyOrder.getPrice().equals(limitOrder.getPrice()));
    }

   
    private static void runTestSortingSellOrdersAppropriatelyAtStartup()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        mCurrentBuyOrder = null;

        mAccountService = new TestAccountService(null);
        mMarketDataService = new TestMarketDataService(new ArrayList<OrderItem>(), new ArrayList<OrderItem>());

        Order order1 = new Order();
        order1.setSide("sell");
        order1.setPrice("8200.00");
        order1.setSize("1");

        Order order2 = new Order();
        order2.setSide("sell");
        order2.setPrice("8100.00000000");
        order2.setSize("1");

        Order order3 = new Order();
        order3.setSide("sell");
        order3.setPrice("8300.00");
        order3.setSize("1");

        ArrayList<Order> orders = new ArrayList<Order>();
        orders.add(order1);
        orders.add(order2);cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        orders.add(order3);

        mOrderService = new TestOrderService(orders);

        TEST_ITERATIONS = 1;
        runTestLoop();

        assert(mOpenSellOrders.size() == 3);
        assert(mOpenSellOrders.pop().getPrice().doubleValue() == new BigDecimal(8100).doubleValue());
        assert(mOpenSellOrders.pop().getPrice().doubleValue() == new BigDecimal(8200).doubleValue());
        assert(mOpenSellOrders.pop().getPrice().doubleValue() == new BigDecimal(8300).doubleValue()); 
    }


    private static void runTestForSeriesOfBuysUsingPricesOfPreviousOrdersToDriveNewLimits()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        List<BigDecimal> balances = new ArrayList<BigDecimal>();
        balances.add(new BigDecimal(480.00));
        balances.add(new BigDecimal(384.00));
        balances.add(new BigDecimal(307.20));
        balances.add(new BigDecimal(245.76));

        mAccountService = new TestAccountService(balances);

        OrderItem bid1 = new OrderItem();
        bid1.setPrice(new BigDecimal("8825"));
        OrderItem bid2 = new OrderItem();
        bid2.setPrice(new BigDecimal("8575"));
        OrderItem bid3 = new OrderItem();
        bid3.setPrice(new BigDecimal("8200"));
        OrderItem bid4 = new OrderItem();
        bid4.setPrice(new BigDecimal("7777"));

        List<OrderItem> bids = new ArrayList<OrderItem>();
        bids.add(bid1);
        bids.add(bid2);
        bids.add(bid3);
        bids.add(bid4);


        OrderItem ask1 = new OrderItem();
        ask1.setPrice(new BigDecimal("8900"));
        OrderItem ask2 = new OrderItem();
        ask2.setPrice(new BigDecimal("8700"));
        OrderItem ask3 = new OrderItem();
        ask3.setPrice(new BigDecimal("8440"));
        OrderItem ask4 = new OrderItem();
        ask4.setPrice(new BigDecimal("8096"));

        List<OrderItem> asks = new ArrayList<OrderItem>();
        asks.add(ask1);
        asks.add(ask2);
        asks.add(ask3);
        asks.add(ask4);
        
        mMarketDataService = new TestMarketDataService(bids, asks);

        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        TEST_ITERATIONS = 1;
        runTestLoop();

        String[] outputFiles = new File("filled_orders").list(new TxtFileFilter());
        assert(outputFiles.length == 1 && outputFiles[0].endsWith("_" + order1.getSide() + "_1.txt"));
        assert(mFilledOrders.getText().endsWith("\t" + order1.getSize() + "\t" + order1.getPrice() + "\n"));

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9100);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01089);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8810);


        runTestLoop();
        assert(mOpenSellOrders.size() == 2);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01089);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8908);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00899);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8536); 


        runTestLoop();
        assert(mOpenSellOrders.size() == 3);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.00899);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8717);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.0075);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8185);


        runTestLoop();
        assert(mOpenSellOrders.size() == 4);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.0075);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8441);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00632);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 7766);
    }




    private static void runTestForSeriesOfBuysUsingMarketPricesToDriveNewLimits()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        List<BigDecimal> balances = new ArrayList<BigDecimal>();
        balances.add(new BigDecimal(480.00));
        balances.add(new BigDecimal(384.00));
        balances.add(new BigDecimal(307.20));
        balances.add(new BigDecimal(245.76));

        mAccountService = new TestAccountService(balances);

        OrderItem bid1 = new OrderItem();
        bid1.setPrice(new BigDecimal("8700"));
        OrderItem bid2 = new OrderItem();
        bid2.setPrice(new BigDecimal("8200"));
        OrderItem bid3 = new OrderItem();
        bid3.setPrice(new BigDecimal("7500"));
        OrderItem bid4 = new OrderItem();
        bid4.setPrice(new BigDecimal("6750"));

        List<OrderItem> bids = new ArrayList<OrderItem>();
        bids.add(bid1);
        bids.add(bid2);
        bids.add(bid3);
        bids.add(bid4);


        OrderItem ask1 = new OrderItem();
        ask1.setPrice(new BigDecimal("8900"));
        OrderItem ask2 = new OrderItem();
        ask2.setPrice(new BigDecimal("8900"));
        OrderItem ask3 = new OrderItem();
        ask3.setPrice(new BigDecimal("8400"));
        OrderItem ask4 = new OrderItem();
        ask4.setPrice(new BigDecimal("7900"));

        List<OrderItem> asks = new ArrayList<OrderItem>();
        asks.add(ask1);
        asks.add(ask2);
        asks.add(ask3);
        asks.add(ask4);
        
        mMarketDataService = new TestMarketDataService(bids, asks);

        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        TEST_ITERATIONS = 1;
        runTestLoop();

        String[] outputFiles = new File("filled_orders").list(new TxtFileFilter());
        assert(outputFiles.length == 1 && outputFiles[0].endsWith("_" + order1.getSide() + "_1.txt"));
        assert(mFilledOrders.getText().endsWith("\t" + order1.getSize() + "\t" + order1.getPrice() + "\n"));

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9100);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01104);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8690);


        runTestLoop();
        assert(mOpenSellOrders.size() == 2);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01104);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8910);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00937);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8190); 


        runTestLoop();
        assert(mOpenSellOrders.size() == 3);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.00937);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8410);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.0082);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 7490);


        runTestLoop();
        assert(mOpenSellOrders.size() == 4);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.0082);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 7910);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00729);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 6740);
    }



    
    private static void runTestForInitialBuyAfterOnePercentDropFromHighestPriceSeen()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();

        mCurrentBuyOrder = null;
        mHighestBidSeen = null;
        mCurrentBid = null;

        cleanFilledOrdersDir();
        mAccountService = new TestAccountService(null);

        OrderItem bid1 = new OrderItem();
        bid1.setPrice(new BigDecimal("8700"));
        OrderItem bid2 = new OrderItem();
        bid2.setPrice(new BigDecimal("8650"));
        OrderItem bid3 = new OrderItem();
        bid3.setPrice(new BigDecimal("8600"));

        List<OrderItem> bids = new ArrayList<OrderItem>();
        bids.add(bid1);
        bids.add(bid2);
        bids.add(bid3);

        mMarketDataService = new TestMarketDataService(bids, new ArrayList<OrderItem>());

        mOrderService = new TestOrderService(new ArrayList<Order>());

        TEST_ITERATIONS = 3;
        runTestLoop();

        assert(mCurrentBuyOrder != null);
        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01396);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8590);
    }



    private static void cleanFilledOrdersDir()
    {
        for (File file: new File("filled_orders").listFiles()) 
        {
            file.delete();
        }
    }

	private static class TxtFileFilter implements FilenameFilter
    {
		public boolean accept(File dir, String name) 
        {
			return name.toLowerCase().endsWith(".txt");
		}
	}
}

