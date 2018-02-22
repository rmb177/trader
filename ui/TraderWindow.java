package ui;

import com.coinbase.exchange.api.entity.NewLimitOrderSingle;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.FlowLayout;
import java.awt.GridLayout;

import java.math.BigDecimal;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;


/**
* Class to encapsulate the GUI for the trading bot.
*/
public class TraderWindow extends JPanel implements ActionListener
{
    //private static JFrame mMainFrame = new JFrame("Trader");
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

    DateFormat mDateFormat = new SimpleDateFormat("M/dd/yyyy HH:mm:ss");
    

    public TraderWindow()
    {
        BorderLayout borderLayout = new BorderLayout(20, 20);
        setLayout(borderLayout);

        createErrorPanel();
        createMarketInfoPanel();
        createOutstandingOrdersPanel();
        createFilledOrdersPanel();
    }


    public Integer getNextPollingInterval()
    {
        return Integer.parseInt(mPollingField.getText()) * 1000 * 60;
    }


    public void setErrorText(String message)
    {
        mErrorLabel.setText(message);
    }


    public void setMarketData(BigDecimal moneyAvailable, BigDecimal highestBid, BigDecimal currentBid, BigDecimal currentAsk)
    {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();
        mAvailableCashLabel.setText(currencyFormat.format(moneyAvailable));
        mHighestBidLabel.setText(currencyFormat.format(highestBid));
        mBidLabel.setText(currencyFormat.format(currentBid));
        mAskLabel.setText(currencyFormat.format(currentAsk));
    }
         


    public void setLastUpdatedText(Date date)
    {
        mLastUpdatedLabel.setText("Last Update : " + mDateFormat.format(date));
    }


    /**
    * Update the current buy order table data and (en|dis)able the Cancel button.
    */
    public void displayCurrentBuyOrder(NewLimitOrderSingle buyOrder)
    {
        mCancelBuyOrderButton.setEnabled(buyOrder != null);
        mBuyTableModel.setData(buyOrder);
    }


    /**
    * Update the current sell orders table data.
    */
    public void displayCurrentSellOrders(Stack<NewLimitOrderSingle> orders)
    {
        NewLimitOrderSingle[] tempSells = new NewLimitOrderSingle[orders.size()];
        orders.copyInto(tempSells);
        mSellTableModel.setData(tempSells);
    }


    /**
    * Function to handle the Cancel Buy Order button being clicked.
    */
    public void actionPerformed(ActionEvent e) 
    {
        if ("cancelBuyOrder".equals(e.getActionCommand())) 
        {
            //mOrderService.cancelOrder(mCurrentBuyOrderId);
            //mCurrentBuyOrderId = null;
            //mCurrentBuyOrder = null;
            //mBuyTableModel.setData(mCurrentBuyOrder);
            mCancelBuyOrderButton.setEnabled(false);
        } 
    }


    /**
    * Writes the given order to the output area where we record fulfilled orders.
    */
    public void writeOrderToScreen(NewLimitOrderSingle order, Date date)
    {
        String outputText = mDateFormat.format(date) + "\t" + order.getSide() + "\t" + order.getSize() + "\t" + order.getPrice() + "\n";
        mFilledOrders.append(outputText); 
    } 




    /**
    * Panel that holds a single lable to report any errors.
    */
    private void createErrorPanel()
    {
        JPanel errorPanel = new JPanel();
        errorPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        errorPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mErrorLabel.setForeground(Color.RED);
        errorPanel.add(mErrorLabel);

        add(errorPanel, BorderLayout.PAGE_START);
    }



    /**
    * Panel that holds current market/account information
    */
    private void createMarketInfoPanel()
    {
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

        add(dataPanel, BorderLayout.LINE_START);
    } 



    /**
    * Panel that holds information about current outstanding buy/sell orders.
    */
    private void createOutstandingOrdersPanel()
    {
        JPanel outstandingOrdersPanel = new JPanel();
        outstandingOrdersPanel.setLayout(new BoxLayout(outstandingOrdersPanel, BoxLayout.Y_AXIS));
        outstandingOrdersPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Cancel current buy order
        outstandingOrdersPanel.add(mCancelBuyOrderButton);
        mCancelBuyOrderButton.setEnabled(false);
        mCancelBuyOrderButton.setActionCommand("cancelBuyOrder");
        mCancelBuyOrderButton.addActionListener(this);
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

        add(outstandingOrdersPanel, BorderLayout.CENTER);
    }



    /**
    * Panel that prints out filled orders and shows time last pinged the exchange.
    */
    private void createFilledOrdersPanel()
    {
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

        add(bottomPanel,BorderLayout.PAGE_END);
    }



    /**
    * Table model to display the current outstanding buy order
    */
    private static class BuyTableModel extends AbstractTableModel
    {
        private NewLimitOrderSingle order;
        private String[] data = new String[3];

        private static final String[] columnNames = {"Coins", "Limit Price", "Total"};

        public void setData(NewLimitOrderSingle order)
        {
            data[0] = order != null ? order.getSize().toString()    : "";
            data[1] = order != null ? order.getPrice().toString()   : "";
            data[2] = order != null ? String.valueOf(order.getPrice().doubleValue() * order.getSize().doubleValue()) : "";

            mOpenBuyTable.invalidate();
            mOpenBuyTable.repaint();
        }

        public int getRowCount()        { return 1; }
        public int getColumnCount()     { return 3; }


        public Object getValueAt(int row, int column)
        {
            return data[column];
        }

        public String getColumnName(int index)
        {
            return columnNames[index];
        }
    }



    /**
    * Table model to display outstanding sell orders.
    */
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

        public int getRowCount()    { return data != null ? data.length : 0; }           
        public int getColumnCount() { return 3; }


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

}
