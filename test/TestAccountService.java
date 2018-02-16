package test;

import java.math.BigDecimal;
import java.util.List;

import com.coinbase.exchange.api.accounts.Account;
import com.coinbase.exchange.api.accounts.AccountServiceInterface;

public class TestAccountService implements AccountServiceInterface
{
    private int numCalls = 0;
    private List<BigDecimal> balances;

    public TestAccountService(List<BigDecimal> balances)
    {
        this.balances = balances;
    }


    public Account getAccount(String id) 
    {
        if (null != balances && balances.size() > numCalls)
        {
            return new Account("ABC", "USD", new BigDecimal(0.00), balances.get(numCalls++), new BigDecimal(0.00), "");
        }
        return new Account("ABC", "USD", new BigDecimal(0.00), new BigDecimal(600.00), new BigDecimal(0.00), "");
    }
}
