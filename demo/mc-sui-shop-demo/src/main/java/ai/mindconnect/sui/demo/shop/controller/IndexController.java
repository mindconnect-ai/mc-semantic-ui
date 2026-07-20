package ai.mindconnect.sui.demo.shop.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Sends visitors of {@code /} to the product list. */
@RestController
public class IndexController {

    @GetMapping("/")
    public ResponseEntity<Void> index() {
        var headers = new HttpHeaders();
        headers.setLocation(URI.create("/admin/products"));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
