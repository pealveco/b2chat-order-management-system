package com.b2chat.ordermanagement.redis.product;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductCachePort;
import com.b2chat.ordermanagement.redis.template.helper.ReactiveTemplateAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Repository
public class ProductCacheAdapter extends ReactiveTemplateAdapterOperations<Product, ProductCacheData>
        implements ProductCachePort {
    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCTS_ALL_KEY = "products:all";
    private final ReactiveRedisTemplate<String, String> stringTemplate;

    public ProductCacheAdapter(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper mapper) {
        super(connectionFactory, mapper, data -> new Product(
                data.getId(),
                data.getName(),
                data.getDescription(),
                new Money(data.getPrice()),
                data.getStock(),
                data.getActive()
        ));
        this.stringTemplate = new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
    }

    @Override
    public Mono<Void> put(Product product) {
        if (!product.getActive()) {
            return evict(product.getId());
        }
        return saveProduct(product)
                .then(indexProduct(product))
                .then()
                .onErrorMap(DataAccessException.class,
                error -> new RepositoryUnavailableException("Product cache is temporarily unavailable"));
    }

    @Override
    public Mono<Void> evict(UUID productId) {
        return deleteProduct(productId)
                .then(unindexProduct(productId))
                .then()
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Product cache is temporarily unavailable"));
    }

    @Override
    public Flux<Product> getAll() {
        return findCachedProductIds()
                .collectList()
                .flatMapMany(this::findProductsByCachedIds)
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Product cache is temporarily unavailable"));
    }

    @Override
    public Mono<Void> putAll(List<Product> products) {
        return Flux.fromIterable(products)
                .flatMap(this::put)
                .then();
    }

    @Override
    protected ProductCacheData toValue(Product product) {
        return new ProductCacheData(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().getAmount(),
                product.getStock(),
                product.getActive()
        );
    }

    private Flux<Product> findProductsByCachedIds(List<String> productIds) {
        if (productIds.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(productIds)
                .flatMap(this::findCachedProduct)
                .filter(Product::getActive)
                .collectList()
                .flatMapMany(products -> products.size() == productIds.size()
                        ? Flux.fromIterable(products)
                        : Flux.empty());
    }

    protected Mono<Product> saveProduct(Product product) {
        return save(productKey(product.getId().toString()), product);
    }

    protected Mono<Long> deleteProduct(UUID productId) {
        return deleteById(productKey(productId.toString()));
    }

    protected Mono<Long> indexProduct(Product product) {
        return stringTemplate.opsForSet().add(PRODUCTS_ALL_KEY, product.getId().toString());
    }

    protected Mono<Long> unindexProduct(UUID productId) {
        return stringTemplate.opsForSet().remove(PRODUCTS_ALL_KEY, productId.toString());
    }

    protected Flux<String> findCachedProductIds() {
        return stringTemplate.opsForSet().members(PRODUCTS_ALL_KEY);
    }

    protected Mono<Product> findCachedProduct(String productId) {
        return findById(productKey(productId));
    }

    private String productKey(String productId) {
        return PRODUCT_KEY_PREFIX + productId;
    }
}
