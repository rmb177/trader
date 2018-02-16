package com.coinbase.exchange.api.orders;

import com.coinbase.exchange.api.entity.NewOrderSingle;

import java.util.List;

public interface OrderServiceInterface 
{

    public Order createOrder(NewOrderSingle order); 

    public String cancelOrder(String orderId); 

    public List<Order> getOpenOrders(); 
}


