package com.ecommerce.marketplace.infrastructure.web;

/**
 * Raw, presentation-layer backing bean for the create-product form. Holds untrusted strings
 * exactly as submitted; the controller turns them into validated domain value objects (via the
 * {@code of(...)} factories, accumulating failures with Vavr {@code Validation}) before any
 * {@code application} type is constructed. Mutable getters/setters are required by Spring MVC
 * data binding and Thymeleaf {@code th:field}; this bean never crosses into the hexagon.
 */
public class ProductForm {

    private String sku = "";
    private String name = "";
    private String description = "";
    private String category = "";
    private String price = "";
    private String stock = "";
    private String weightKg = "";

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public String getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(String weightKg) {
        this.weightKg = weightKg;
    }
}
