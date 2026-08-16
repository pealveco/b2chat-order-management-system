package com.b2chat.ordermanagement.r2dbc.user;

import com.b2chat.ordermanagement.model.common.RepositoryUnavailableException;
import com.b2chat.ordermanagement.model.email.Email;
import com.b2chat.ordermanagement.model.user.EmailAlreadyExistsException;
import com.b2chat.ordermanagement.model.user.User;
import com.b2chat.ordermanagement.model.user.gateways.UserRepository;
import com.b2chat.ordermanagement.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class UserReactiveRepositoryAdapter extends ReactiveAdapterOperations<
    User,
    UserData,
    UUID,
    UserReactiveRepository
> implements UserRepository {
    public UserReactiveRepositoryAdapter(UserReactiveRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> new User(
                data.getId(),
                new Email(data.getEmail()),
                data.getName(),
                data.getAddress()
        ));
    }

    @Override
    protected UserData toData(User user) {
        return new UserData(
                user.getId(),
                user.getEmail().getValue(),
                user.getName(),
                user.getAddress()
        );
    }

    @Override
    public Mono<User> save(User user) {
        return super.save(user)
                .onErrorMap(DataIntegrityViolationException.class,
                        error -> new EmailAlreadyExistsException(user.getEmail().getValue()))
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Persistence repository is temporarily unavailable"));
    }

    @Override
    public Mono<Boolean> existsByEmail(Email email) {
        return repository.existsByEmail(email.getValue())
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Persistence repository is temporarily unavailable"));
    }

    @Override
    public Mono<User> findById(UUID id) {
        return super.findById(id)
                .onErrorMap(DataAccessException.class,
                        error -> new RepositoryUnavailableException("Persistence repository is temporarily unavailable"));
    }
}
