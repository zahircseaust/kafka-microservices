package com.example.inventoryservice.util;

import com.example.inventoryservice.dto.ProductResponse;
import com.example.inventoryservice.model.Product;
import org.springframework.stereotype.Component;

@Component
public class Util {

    public ProductResponse mapToResponse(Product product) {

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .stockQuantity(product.getStockQuantity())
                .version(product.getVersion())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
