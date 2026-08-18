package com.b2chat.ordermanagement.r2dbc.transaction;

import com.b2chat.ordermanagement.model.common.gateways.TransactionPort;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class R2dbcTransactionAdapter implements TransactionPort {
    private final TransactionalOperator transactionalOperator;

    public R2dbcTransactionAdapter(ConnectionFactory connectionFactory) {
        var transactionManager = new R2dbcTransactionManager(connectionFactory);
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    @Override
    public <T> Mono<T> transactional(Mono<T> publisher) {
        return transactionalOperator.transactional(publisher);
    }
}
