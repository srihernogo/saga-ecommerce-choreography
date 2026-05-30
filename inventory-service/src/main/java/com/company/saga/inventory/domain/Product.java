package com.company.saga.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Product create(String id, String name, int initialStock) {
        Product p = new Product();
        p.id = id;
        p.name = name;
        p.stock = initialStock;
        p.reserved = 0;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void reserve(int quantity) {
        if (getAvailableStock() < quantity) {
            throw new IllegalStateException("Insufficient stock for product " + id);
        }
        this.reserved += quantity;
        this.updatedAt = Instant.now();
    }

    public void release(int quantity) {
        if (this.reserved < quantity) {
            throw new IllegalStateException("Cannot release more than reserved stock");
        }
        this.reserved -= quantity;
        this.updatedAt = Instant.now();
    }

    public void confirm(int quantity) {
        if (this.reserved < quantity) {
            throw new IllegalStateException("Cannot confirm more than reserved stock");
        }
        this.stock -= quantity;
        this.reserved -= quantity;
        this.updatedAt = Instant.now();
    }

    public int getAvailableStock() {
        return this.stock - this.reserved;
    }
}
