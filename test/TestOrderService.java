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
    private static int id = 10; 

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
        orders.add(newOrder);
  
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



    // Testing function to allow us to remove the last order of the given type
    // to simulate that the order was filled.
    public void fulfillLastOrder(String type)
    {
        for (int x = orders.size() - 1; x >= 0; --x)
        {
            if (orders.get(x).getSide().equals(type))
            {
                orders.remove(x);
                break;
            }
        }
    }
}


