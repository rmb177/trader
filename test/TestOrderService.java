package test;

import com.coinbase.exchange.api.entity.NewLimitOrderSingle;
import com.coinbase.exchange.api.entity.NewOrderSingle;
import com.coinbase.exchange.api.orders.Order;
import com.coinbase.exchange.api.orders.OrderServiceInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestOrderService implements OrderServiceInterface
{
    private List<Order> orders;
    private static int id = 0; 

    public TestOrderService(List<Order> orders)
    {
        this.orders = orders;
    }

    public Order createOrder(NewOrderSingle order)
    {
        NewLimitOrderSingle limitOrder = (NewLimitOrderSingle)order;
        Order newOrder = new Order();
        newOrder.setSize(limitOrder.getSize().toString());
        newOrder.setId(String.valueOf(++id));
        newOrder.setPrice(limitOrder.getPrice().toString());
        newOrder.setStatus("pending");
        newOrder.setSide(order.getSide());

        if (order.getSide().equals("sell"))
        {
            orders.add(newOrder);
        }
  
        return newOrder;
    } 

    public String cancelOrder(String orderId)
    {
        orders = orders.stream().filter(order -> !order.getId().equals(orderId)).collect(Collectors.toList());
        return orderId;
    }

    public List<Order> getOpenOrders()
    {
        return orders; 
    }

}


