package com.luukien.javacard.service;

import com.luukien.javacard.model.Product;
import com.luukien.javacard.sql.SqlQueries;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductService {

    @Getter
    private static final ProductService instance = new ProductService();

    public List<Product> loadProducts() {
        List<Product> items = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getGET_PRODUCTS())) {
                return getProducts(items, ps);
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
        return items;
    }

    public List<Product> filterProducts(String filter) {
        List<Product> items = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getFILTER_PRODUCT_BY_NAME_OR_CODE())) {
                ps.setString(1, filter);
                return getProducts(items, ps);
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
        return items;
    }

    private List<Product> getProducts(List<Product> items, PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String name = rs.getString("name");
            String code = rs.getString("code");
            Long id = rs.getLong("id");
            int remain = rs.getInt("remain");
            BigDecimal price = rs.getBigDecimal("price");
            Product product = new Product(id, code, name, remain, price);
            items.add(product);
        }
        return items;
    }
}
