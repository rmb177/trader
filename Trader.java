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
import javax.swing.JButton;
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
    private final static boolean TESTING = true;

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
    private static JTextField mPollingField = new JTextField("2");
    private static JButton mCancelBuyOrderButton = new JButton("Cancel Buy Order");
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


    private static String mCurrentBuyOrderId = null;

    private static NewLimitOrderSingle mLastFulfilledSell = null;
    private static boolean mDone;

   
    // Can only go up to 8 in loop since the loop can push onto stack and reach index 9 
    private static final int MAX_OPEN_SELL_ORDERS_TO_PROCESS_LOOP = 8;
    private static double[] targetBuyPercentages =  {0.0050, 0.010, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09};
    private static double[] targetSellPercentages = {0.0025, 0.005, 0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08};


    private static final double MAX_HIGH_BID_REACHED_BEFORE_CANCELING_LONE_BUY_ORDER = 0.01; 

    
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

        
        // Outstanding Orders
        JPanel outstandingOrdersPanel = new JPanel();
        outstandingOrdersPanel.setLayout(new BoxLayout(outstandingOrdersPanel, BoxLayout.Y_AXIS));
        outstandingOrdersPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        
        outstandingOrdersPanel.add(mCancelBuyOrderButton);
        mCancelBuyOrderButton.setEnabled(false);
        mCancelBuyOrderButton.setActionCommand("cancelBuyOrder");
        mCancelBuyOrderButton.addActionListener(new CancelOrderActionListener());
        outstandingOrdersPanel.add(Box.createRigidArea(new Dimension(0, 20)));

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
                    mErrorLabel.invalidate();
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
    } 



    /**
    * Get all the open sell orders. If we have less orders than are what are on the stack
    * pop off the top orders until they are the same.
    */
    private static void checkSellOrders(List<Order> openOrders)
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
                NewLimitOrderSingle order = mOpenSellOrders.pop();
                Date date = new Date();
                writeOrderToDisk(order, date);
                writeOrderToScreen(order, date);
                mLastFulfilledSell = order;
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
        if (mLastFulfilledSell != null && mCurrentBuyOrder != null && buyOrders.size() == 1)
        {
            clearBuyOrders(buyOrders);
        }

        if (buyOrders.size() > 1)
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
                    Order order = buyOrders.get(0); 
                    mCurrentBuyOrderId = order.getId();
                    mCurrentBuyOrder = new NewLimitOrderSingle(order); 
                    mBuyTableModel.setData(mCurrentBuyOrder);
                    mCancelBuyOrderButton.setEnabled(true);
                }
            }
        }
        else if (buyOrders.size() == 0 && mOpenSellOrders.size() <= MAX_OPEN_SELL_ORDERS_TO_PROCESS_LOOP)
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
                double sellTargetPercentIncrease = targetSellPercentages[mOpenSellOrders.size()]; 
                double sellTargetPrice = lastBuyPrice + (lastBuyPrice * sellTargetPercentIncrease);
                long sellLimitPrice = Math.round((Math.max(sellTargetPrice, mCurrentAsk.doubleValue()) + 5));

                Order returnedSellOrder = createOrder("sell", mCurrentBuyOrder.getSize().doubleValue(), (int)sellLimitPrice); 
                if (returnedSellOrder != null && !returnedSellOrder.getStatus().equals("rejected"))
                {
                    mOpenSellOrders.push(new NewLimitOrderSingle(returnedSellOrder));
                    NewLimitOrderSingle[] tempSells = new NewLimitOrderSingle[mOpenSellOrders.size()];
                    mOpenSellOrders.copyInto(tempSells);
                    mSellTableModel.setData(tempSells);
                
                    double buyTargetPrice = lastBuyPrice - (lastBuyPrice * targetBuyPercentages[mOpenSellOrders.size()]);
                    long buyLimitPrice = Math.round((Math.min(buyTargetPrice, mCurrentBid.doubleValue()) - 5));
                    double dollarAmount = mUSDAccount.getAvailable().doubleValue() * .2;
                    double buyOrderSize = dollarAmount / buyLimitPrice;


                    Order returnedBuyOrder = createOrder("buy", buyOrderSize, (int)buyLimitPrice); 
                    if (returnedBuyOrder != null && !returnedBuyOrder.getStatus().equals("rejected"))
                    {
                        mCurrentBuyOrderId = returnedBuyOrder.getId();
                        mCurrentBuyOrder = new NewLimitOrderSingle(returnedBuyOrder);
                        mBuyTableModel.setData(mCurrentBuyOrder);
                        mCancelBuyOrderButton.setEnabled(true);
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
                    // UPDATE ME AT SOME POINT  
                    double valueToUse = mLastFulfilledSell == null ? mCurrentBid.doubleValue() : mLastFulfilledSell.getPrice().doubleValue() - 5;
                    double lastBuyPrice = (valueToUse - 5) * (1 - targetSellPercentages[mOpenSellOrders.size()]);
                    buyLimitPrice = Math.round((Math.min(lastBuyPrice, mCurrentBid.doubleValue()) - 5));
                }
                else if (mHighestBidSeen != null &&
                         mCurrentBid != null &&
                         ((mHighestBidSeen.doubleValue() - mCurrentBid.doubleValue()) / mHighestBidSeen.doubleValue()) > targetBuyPercentages[0])
                {
                    buyLimitPrice = mCurrentBid.longValue() - 5;
                }

                if (buyLimitPrice != -1)
                {
                    double dollarAmount = mUSDAccount.getAvailable().doubleValue() * .2;
                    double buyOrderSize = dollarAmount / buyLimitPrice;
                
                    Order returnedBuyOrder = createOrder("buy", buyOrderSize, buyLimitPrice); 
                    if (returnedBuyOrder != null && !returnedBuyOrder.getStatus().equals("rejected"))
                    {
                        mCurrentBuyOrderId = returnedBuyOrder.getId();
                        mCurrentBuyOrder = new NewLimitOrderSingle(returnedBuyOrder);
                        mBuyTableModel.setData(mCurrentBuyOrder);
                        mCancelBuyOrderButton.setEnabled(true);
                    }
                }
            }
        }

        mLastFulfilledSell = null;
    }


    private static Order createOrder(String side, double orderSize, long limitPrice)
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
    * Something screwed up so just clear out all buy orders and
    * start from scratch.
    */
    private static void clearBuyOrders(List<Order> buyOrders)
    {
        for (Order order : buyOrders)
        {
            mOrderService.cancelOrder(order.getId());
        }
        buyOrders.clear();

        mCurrentBuyOrderId = null;
        mCurrentBuyOrder = null;
        mBuyTableModel.setData(mCurrentBuyOrder);
        mCancelBuyOrderButton.setEnabled(false);
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
                    if (!TESTING)
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


    private static class CancelOrderActionListener implements ActionListener
    {
        public void actionPerformed(ActionEvent e) 
        {
            if ("cancelBuyOrder".equals(e.getActionCommand())) 
            {
                mOrderService.cancelOrder(mCurrentBuyOrderId);
                mCurrentBuyOrderId = null;
                mCurrentBuyOrder = null;
                mBuyTableModel.setData(mCurrentBuyOrder);
                mCancelBuyOrderButton.setEnabled(false);
            } 
        }
    }

    private static class BuyTableModel extends AbstractTableModel
    {
        private NewLimitOrderSingle data;
        private final String[] columnNames = {"Coins", "Limit Price", "Total"};

        public void setData(NewLimitOrderSingle order)
        {
            data = order;
            mOpenBuyTable.invalidate();
            mOpenBuyTable.repaint();
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
            mOpenSellsTable.invalidate();
            mOpenSellsTable.repaint();
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
    private static void runTestLoop(int numIterations)
    {
        try
        {
            for (int x = 0; x < numIterations; ++x)
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
        runTestForSeriesOfBuysThenASellUsingPricesOfPreviousOrdersToDriveNewLimits();
        runTestForSeriesOfBuysThenASellUsingMarketPricesToDriveNewLimits();
        runTestForNotEnoughBitcoinsInOrder();
        runTestForNotEnoughCash();
        runTestForCancelingBuyAfterRunUp();
        runTestForTypicalSituation();
    }



    private static void runTestForMultipleOpenBuysFromExchange()
    {
        setupTest();
        setupTestAccountService(new double[] {});
        setupMarketDataService(new double[] {}, new double[] {});
        
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

        runTestLoop(1);
        assert(mErrorLabel.getText().equals("You have more than one buy order open!!"));
        assert(mOrderService.getOpenOrders().size() == 0);
        assert(mCurrentBuyOrder == null);
    }

    private static void runTestForDifferentBuyOrderFromExchange()
    {
        setupTest();
        setupTestAccountService(new double[] {});
        setupMarketDataService(new double[] {}, new double[] {});
        
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

        runTestLoop(1);
        assert(mErrorLabel.getText().equals("Your current buy order does not match the one you have in memory!!"));
        assert(mOrderService.getOpenOrders().size() == 0);
        assert(mCurrentBuyOrder == null);
    }


    private static void runTestForOneOpenBuyOrderFromExchange()
    {
        setupTest();
        setupTestAccountService(new double[] {});
        setupMarketDataService(new double[] {}, new double[] {});
        
        ArrayList<Order> orders = new ArrayList<Order>();
        Order order1 = new Order();
        order1.setSide("buy");
        order1.setSize("2");
        order1.setPrice("3");
        orders.add(order1);
        mOrderService = new TestOrderService(orders);

        NewLimitOrderSingle limitOrder = new NewLimitOrderSingle(order1);

        runTestLoop(1);
        assert(mOrderService.getOpenOrders().size() == 1);
        assert(mCurrentBuyOrder.getSize().equals(limitOrder.getSize()));
        assert(mCurrentBuyOrder.getPrice().equals(limitOrder.getPrice()));
    }

   
    private static void runTestSortingSellOrdersAppropriatelyAtStartup()
    {
        setupTest();
        setupTestAccountService(new double[] {});
        setupMarketDataService(new double[] {}, new double[] {});
        
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

        runTestLoop(1);

        assert(mOpenSellOrders.size() == 3);
        assert(mOpenSellOrders.pop().getPrice().doubleValue() == new BigDecimal(8100).doubleValue());
        assert(mOpenSellOrders.pop().getPrice().doubleValue() == new BigDecimal(8200).doubleValue());
        assert(mOpenSellOrders.pop().getPrice().doubleValue() == new BigDecimal(8300).doubleValue()); 
    }


    private static void runTestForSeriesOfBuysUsingPricesOfPreviousOrdersToDriveNewLimits()
    {
        setupTest();
        setupTestAccountService(new double[] {480.00, 384.00, 307.20, 245.76});
        setupMarketDataService(new double[] {8911, 8727, 8461, 8117}, new double[] {8949, 8949, 8809, 8624});

        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(1);

        String[] outputFiles = new File("filled_orders").list(new TxtFileFilter());
        assert(outputFiles.length == 1 && outputFiles[0].endsWith("_" + order1.getSide() + "_1.txt"));
        assert(mFilledOrders.getText().endsWith("\t" + order1.getSize() + "\t" + order1.getPrice() + "\n"));

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9028);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01078);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8905);


        runTestLoop(1);
        assert(mOpenSellOrders.size() == 2);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01078);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8955);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00880);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8722); 


        runTestLoop(1);
        assert(mOpenSellOrders.size() == 3);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.00880);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8814);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00726);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8455);


        runTestLoop(1);
        assert(mOpenSellOrders.size() == 4);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.00726);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8629);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00605);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8112);
    }




    private static void runTestForSeriesOfBuysUsingMarketPricesToDriveNewLimits()
    {
        setupTest();
        setupTestAccountService(new double[] {480.00, 384.00, 307.20, 245.76});
        setupMarketDataService(new double[] {8900, 8700, 8400, 8000}, new double[] {8949, 8949, 8810, 8624});

        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(1);

        String[] outputFiles = new File("filled_orders").list(new TxtFileFilter());
        assert(outputFiles.length == 1 && outputFiles[0].endsWith("_" + order1.getSide() + "_1.txt"));
        assert(mFilledOrders.getText().endsWith("\t" + order1.getSize() + "\t" + order1.getPrice() + "\n"));

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9028);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01079);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8895);


        runTestLoop(1);
        assert(mOpenSellOrders.size() == 2);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01079);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8954);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00883);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8695); 


        runTestLoop(1);
        assert(mOpenSellOrders.size() == 3);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.00883);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8815);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00731);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8395);


        runTestLoop(1);
        assert(mOpenSellOrders.size() == 4);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.00731);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 8629);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00614);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 7995);
    }



    
    private static void runTestForInitialBuyAfterOnePercentDropFromHighestPriceSeen()
    {
        setupTest();
        setupTestAccountService(new double[] {});
        setupMarketDataService(new double[] {8700, 8675, 8650}, new double[] {});
        
        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(3);

        assert(mCurrentBuyOrder != null);
        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01388);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8645);
    }





    private static void runTestForSeriesOfBuysThenASellUsingPricesOfPreviousOrdersToDriveNewLimits()
    {
        setupTest();
        setupTestAccountService(new double[] {480.00, 580.00, 580.00});
        setupMarketDataService(new double[] {8911, 8906}, new double[] {8949, 8949, 8988});
        

        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(2);

        ((TestOrderService)mOrderService).fulfillLastSellOrder();
        
        // Add back buy order so we still have one open
        ((TestOrderService)mOrderService).addBackBuyOrder(mCurrentBuyOrder);

        runTestLoop(1);

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9028);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01303);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8900); 
    }



    private static void runTestForSeriesOfBuysThenASellUsingMarketPricesToDriveNewLimits()
    {
        setupTest();
        setupTestAccountService(new double[] {480.00, 580.00, 580.00});
        setupMarketDataService(new double[] {8911, 8600}, new double[] {8949, 8949, 8988});
        
        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(2);

        ((TestOrderService)mOrderService).fulfillLastSellOrder();

        // Add back buy order so we still have one open
        ((TestOrderService)mOrderService).addBackBuyOrder(mCurrentBuyOrder);


        runTestLoop(1);

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9028);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01349);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8595); 
    }



    private static void runTestForNotEnoughBitcoinsInOrder()
    {
        setupTest();
        setupTestAccountService(new double[] {150.00, 150.00, 150.00});
        setupMarketDataService(new double[] {35000, 34999, 32000}, new double[] {});


        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(3);

        assert(mCurrentBuyOrder == null);
    }



    private static void runTestForNotEnoughCash()
    {
        setupTest();
        setupTestAccountService(new double[] {40.00, 40.00, 40.00});
        setupMarketDataService(new double[] {1000, 999, 870}, new double[] {});
        
        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(3);

        assert(mCurrentBuyOrder == null);
    }



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



    private static void runTestForTypicalSituation()
    {
        setupTest();
        setupTestAccountService(new double[] {480.00, 480.00, 384.00});
        setupMarketDataService(new double[] {8900, 8900, 8700}, new double[] {8949, 8949, 8701});

        NewLimitOrderSingle order1 = new NewLimitOrderSingle();
        order1.setSide("buy");
        order1.setSize(new BigDecimal(0.01333));
        order1.setPrice(new BigDecimal(9000));
        mCurrentBuyOrder = order1;

        mOrderService = new TestOrderService(new ArrayList<Order>());

        runTestLoop(1);

        assert(mOpenSellOrders.size() == 1);
        assert(mOpenSellOrders.peek().getSize().doubleValue() == 0.01333);
        assert(mOpenSellOrders.peek().getPrice().doubleValue() == 9028);

        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.01079);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8895);

        
        // Sell order goes through on server
        ((TestOrderService)mOrderService).fulfillLastSellOrder();
        ((TestOrderService)mOrderService).addBackBuyOrder(mCurrentBuyOrder);

        runTestLoop(1);
        assert(mOpenSellOrders.size() == 0);
        assert(mCurrentBuyOrder == null);


        runTestLoop(1);
        assert(mCurrentBuyOrder.getSize().doubleValue() == 0.00883);
        assert(mCurrentBuyOrder.getPrice().doubleValue() == 8695);

        
    }


    private static void setupTest()
    {
        cleanFilledOrdersDir();
        mOpenSellOrders.clear();
        
        mHighestBidSeen = null;
        mCurrentBid = null;
        mCurrentAsk = null;
        mCurrentBuyOrder = null;
        mLastFulfilledSell = null;
    }



    private static void setupTestAccountService(double[] balances)
    {
        List<BigDecimal> balanceList = new ArrayList<BigDecimal>();
        for (int x = 0; x < balances.length; ++x)
        {
            balanceList.add(new BigDecimal(balances[x]));
        }
        mAccountService = new TestAccountService(balanceList);
    }


    private static void setupMarketDataService(double[] bids, double[] asks)
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
        
        mMarketDataService = new TestMarketDataService(bidsList, asksList);
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
