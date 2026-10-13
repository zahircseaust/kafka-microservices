package com.example.inventoryservice.controller;

import com.example.inventoryservice.dto.ApiResponse;
import com.example.inventoryservice.dto.ProductRequest;
import com.example.inventoryservice.dto.ProductResponse;
import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.service.InventoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final InventoryService inventoryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            HttpServletRequest httpRequest) {

        List<ProductResponse> products = inventoryService.getAllProducts();

        ApiResponse<List<ProductResponse>> response =
                ApiResponse.<List<ProductResponse>>builder()
                        .success(true)
                        .message("Products retrieved successfully")
                        .data(products)
                        .timestamp(LocalDateTime.now())
                        .path(httpRequest.getRequestURI())
                        .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return inventoryService.getProductById(id);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request,
            HttpServletRequest httpRequest) {

        ProductResponse product =
                inventoryService.createProduct(request);

        ApiResponse<ProductResponse> response =
                ApiResponse.<ProductResponse>builder()
                        .success(true)
                        .message("Product created successfully")
                        .data(product)
                        .timestamp(LocalDateTime.now())
                        .path(httpRequest.getRequestURI())
                        .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id,
                                                 @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(inventoryService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        inventoryService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}