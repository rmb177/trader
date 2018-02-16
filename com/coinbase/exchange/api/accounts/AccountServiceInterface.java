package com.coinbase.exchange.api.accounts;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public interface AccountServiceInterface
{
    public Account getAccount(String id);
}
