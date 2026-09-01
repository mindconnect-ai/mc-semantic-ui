package ai.mindconnect.sui.demo.shop;

import ai.mindconnect.sui.demo.shop.jpa.Customer;
import ai.mindconnect.sui.demo.shop.jpa.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Page<Customer> list(String query, int page, int size) {
        return repository.search(query, PageRequest.of(page - 1, size));
    }
}
