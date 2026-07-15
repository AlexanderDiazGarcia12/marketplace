package com.ecommerce.marketplace.infrastructure.web;

/**
 * Raw, presentation-layer backing bean for the create- and edit-product forms. Holds untrusted
 * strings exactly as submitted; the controller turns them into validated domain value objects before
 * any {@code application} type is constructed. Mutable getters/setters are required by Spring MVC
 * data binding and Thymeleaf {@code th:field}; this bean never crosses into the hexagon.
 *
 * <p>{@code version} is the optimistic {@code @Version} carried as a hidden field on the edit form:
 * it round-trips the version the editor loaded so a concurrent edit is detected on submit. Creation
 * ignores it.</p>
 */
public class ProductForm {

    private String sku = "";
    private String name = "";
    private String description = "";
    private String category = "";
    private String price = "";
    private String stock = "";
    private String weightKg = "";
    private String version = "";

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
