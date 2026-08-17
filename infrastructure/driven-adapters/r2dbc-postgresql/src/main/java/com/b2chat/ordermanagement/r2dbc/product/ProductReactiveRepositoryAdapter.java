package com.b2chat.ordermanagement.r2dbc.product;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.money.Money;
import com.b2chat.ordermanagement.model.product.Product;
import com.b2chat.ordermanagement.model.product.gateways.ProductRepository;
import com.b2chat.ordermanagement.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class ProductReactiveRepositoryAdapter extends ReactiveAdapterOperations<
    Product,
    ProductData,
    UUID,
    ProductReactiveRepository
> implements ProductRepository {
    public ProductReactiveRepositoryAdapter(ProductReactiveRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> new Product(
                data.getId(),
                data.getName(),
                data.getDescription(),
                new Money(data.getPrice()),
                data.getStock(),
                data.getActive()
        ));
    }

    @Override
    protected ProductData toData(Product product) {
        return new ProductData(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().getAmount(),
                product.getStock(),
                product.getActive()
        );
    }

    @Override
    public Mono<Product> save(Product product) {
        return super.save(product)
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Product repository is temporarily unavailable"));
    }

    @Override
    public Mono<Product> findById(UUID id) {
        return super.findById(id)
                .filter(Product::getActive)
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Product repository is temporarily unavailable"));
    }

    @Override
    public Flux<Product> findAll() {
        return repository.findByActiveTrue()
                .map(this::toEntity)
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Product repository is temporarily unavailable"));
    }
}
