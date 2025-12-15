package com.luukien.javacard.service;

import com.luukien.javacard.model.Order;
import com.luukien.javacard.model.OrderItem;
import com.luukien.javacard.utils.DatabaseHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderService {

    public static Order createOrder(Long userId, List<OrderItem> items) {
        if (items.isEmpty()) {
            return null;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getSubTotal());
        }

        String code = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); // Gen unique code

        String sqlOrder = "INSERT INTO orders (code, user_id, total_price, create_at) VALUES (?, ?, ?, ?) RETURNING id";
        String sqlItem = "INSERT INTO order_items (order_code, product_code, product_name, quantity, price) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseHelper.getConnection()) {
            conn.setAutoCommit(false);

            // Insert order
            long orderId;
            try (PreparedStatement psOrder = conn.prepareStatement(sqlOrder)) {
                psOrder.setString(1, code);
                psOrder.setLong(2, userId);
                psOrder.setBigDecimal(3, total);
                psOrder.setObject(4, LocalDateTime.now());
                try (ResultSet rs = psOrder.executeQuery()) {
                    if (rs.next()) {
                        orderId = rs.getLong("id");
                    } else {
                        conn.rollback();
                        return null;
                    }
                }
            }

            // Insert items
            try (PreparedStatement psItem = conn.prepareStatement(sqlItem)) {
                for (OrderItem item : items) {
                    psItem.setString(1, code);
                    psItem.setString(2, item.getProductCode());
                    psItem.setString(3, item.getProductName());
                    psItem.setLong(4, item.getQuantity());
                    psItem.setBigDecimal(5, item.getPrice());
                    psItem.executeUpdate();
                }
            }

            conn.commit();

            return Order.builder()
                    .id(orderId)
                    .code(code)
                    .userId(userId)
                    .totalPrice(total)
                    .createAt(LocalDateTime.now())
                    .build();

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean payOrder(String orderCode, Long userId, BigDecimal amount) {
        BigDecimal balance = DatabaseHelper.getUserBalance(userId);
        if (balance.compareTo(amount) < 0) {
            return false;
        }

        boolean balanceUpdated = DatabaseHelper.updateBalance(userId, amount.negate());

        if (balanceUpdated) {
            // Có thể thêm update order status nếu có cột, nhưng model không có nên skip hoặc add log
            return true;
        }
        return false;
    }

    public static List<Order> getUserOrders(Long userId) {
        List<Order> orders = new ArrayList<>();
        String sqlOrder = "SELECT * FROM orders WHERE user_id = ? ORDER BY create_at DESC";
        String sqlItems = "SELECT * FROM order_items WHERE order_code = ?";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement psOrder = conn.prepareStatement(sqlOrder)) {
            psOrder.setLong(1, userId);
            try (ResultSet rsOrder = psOrder.executeQuery()) {
                while (rsOrder.next()) {
                    Order order = Order.builder()
                            .id(rsOrder.getLong("id"))
                            .code(rsOrder.getString("code"))
                            .userId(rsOrder.getLong("user_id"))
                            .totalPrice(rsOrder.getBigDecimal("total_price"))
                            .createAt(rsOrder.getObject("create_at", LocalDateTime.class))
                            .build();

                    // Get items
                    List<OrderItem> items = getOrderItems(order.getCode());
                    // Note: Model Order không có setItems, nên nếu cần, add field List<OrderItem> items vào Order và setter
                    // Giả sử add @Setter vào Order hoặc reflect, nhưng tốt hơn update model
                    orders.add(order);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    private static List<OrderItem> getOrderItems(String orderCode) {
        List<OrderItem> items = new ArrayList<>();
        String sql = "SELECT * FROM order_items WHERE order_code = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(OrderItem.builder()
                            .id(rs.getLong("id"))
                            .orderCode(rs.getString("order_code"))
                            .productCode(rs.getString("product_code"))
                            .productName(rs.getString("product_name"))
                            .quantity(rs.getLong("quantity"))
                            .price(rs.getBigDecimal("price"))
                            .build());
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }
}