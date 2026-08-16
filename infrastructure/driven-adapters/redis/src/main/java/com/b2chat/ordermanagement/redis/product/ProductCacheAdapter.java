package com.b2chat.ordermanagement.redis.product;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.redis.template.helper.ReactiveTemplateAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class ProductCacheAdapter extends ReactiveTemplateAdapterOperations<Product, ProductCacheData>
        implements ProductCachePort {
    private static final String PRODUCT_KEY_PREFIX = "product:";

    public ProductCacheAdapter(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper mapper) {
        super(connectionFactory, mapper, data -> new Product(
                data.getId(),
                data.getName(),
                data.getDescription(),
                new Money(data.getPrice()),
                data.getStock()
        ));
    }

    @Override
    public Mono<Void> put(Product product) {
        return save(PRODUCT_KEY_PREFIX + product.getId(), product)
                .then()
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Product cache is temporarily unavailable"));
    }

    @Override
    protected ProductCacheData toValue(Product product) {
        return new ProductCacheData(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().getAmount(),
                product.getStock()
        );
    }
}
