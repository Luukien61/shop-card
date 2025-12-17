package com.luukien.javacard.service;

import com.luukien.javacard.exception.OrderException;
import com.luukien.javacard.model.Order;
import com.luukien.javacard.model.OrderItem;
import com.luukien.javacard.model.UserCardInfo;
import com.luukien.javacard.utils.CardHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderService {
    @Getter
    private static final OrderService instance = new OrderService();


    public Boolean isCardVerified() throws Exception {
        return CardHelper.isCardVerified();
    }

    public UserCardInfo getUserCardInfo(String pin) throws Exception {
        return CardHelper.getUserCardInfo(pin);
    }

    public List<Order> loadOrder() {

        List<Order> orders = new ArrayList<>();

        String sql =
                "SELECT id, code, user_phone, total_price, create_at " +
                        "FROM orders " +
                        "WHERE create_at >= NOW() - INTERVAL '7 days' " +
                        "ORDER BY create_at DESC";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Order order = new Order();
                order.setId(rs.getLong("id"));
                order.setCode(rs.getString("code"));
                order.setUserPhone(rs.getString("user_phone"));
                order.setTotalPrice(rs.getBigDecimal("total_price"));

                order.setCreateAt(
                        rs.getTimestamp("create_at").toLocalDateTime()
                );

                orders.add(order);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return orders;
    }

    public List<Order> loadOrdersByDate(LocalDate date) {

        List<Order> orders = new ArrayList<>();

        String sql =
                "SELECT id, code, user_phone, total_price, create_at " +
                        "FROM orders " +
                        "WHERE create_at >= ? AND create_at < ? " +
                        "ORDER BY create_at DESC";

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(startOfDay));
            ps.setTimestamp(2, Timestamp.valueOf(startOfNextDay));

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Order order = new Order();
                order.setId(rs.getLong("id"));
                order.setCode(rs.getString("code"));
                order.setUserPhone(rs.getString("user_phone"));
                order.setTotalPrice(rs.getBigDecimal("total_price"));
                order.setCreateAt(
                        rs.getTimestamp("create_at").toLocalDateTime()
                );
                orders.add(order);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return orders;
    }

    public List<OrderItem> loadOrderItemsByOrderCode(String orderCode) {

        List<OrderItem> items = new ArrayList<>();

        String sql =
                "SELECT oi.id, oi.order_code, oi.product_code, " +
                        "       p.name AS product_name, " +
                        "       oi.quantity, oi.price " +
                        "FROM order_item oi " +
                        "JOIN products p ON oi.product_code = p.code " +
                        "WHERE oi.order_code = ? " +
                        "ORDER BY oi.id";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, orderCode);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                OrderItem item = OrderItem.builder()
                        .id(rs.getLong("id"))
                        .orderCode(rs.getString("order_code"))
                        .productCode(rs.getString("product_code"))
                        .productName(rs.getString("product_name"))
                        .quantity(rs.getInt("quantity"))
                        .price(rs.getBigDecimal("price"))
                        .build();

                items.add(item);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return items;
    }



    public void createOrder(String phone, List<OrderItem> items) throws OrderException {

        String selectUserSql =
                "SELECT balance FROM users WHERE phone = ? FOR UPDATE";

        String insertOrderSql =
                "INSERT INTO orders (code, user_phone, total_price) VALUES (?, ?, ?)";

        String insertOrderItemSql =
                "INSERT INTO order_item (order_code, product_code, quantity, price) VALUES (?, ?, ?, ?)";

        String updateProductSql =
                "UPDATE products SET remain = remain - ? WHERE code = ? AND remain >= ?";

        String updateBalanceSql =
                "UPDATE users SET balance = balance - ?, updated_at = NOW() WHERE phone = ?";

        Connection conn = null;

        try {
            conn = DatabaseHelper.getConnection();
            conn.setAutoCommit(false);

            BigDecimal userBalance;
            try (PreparedStatement ps = conn.prepareStatement(selectUserSql)) {
                ps.setString(1, phone);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    throw new OrderException("Không tìm thấy người dùng");
                }

                userBalance = rs.getBigDecimal("balance");
                if (userBalance == null) {
                    userBalance = BigDecimal.ZERO;
                }
            }


            BigDecimal totalPrice = BigDecimal.ZERO;
            for (OrderItem item : items) {
                totalPrice = totalPrice.add(item.getSubTotal());
            }

            if (totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new OrderException("Giỏ hàng không hợp lệ");
            }

            if (userBalance.compareTo(totalPrice) < 0) {
                throw new OrderException("Số dư không đủ để thanh toán");
            }


            String orderCode = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

            try (PreparedStatement ps = conn.prepareStatement(insertOrderSql)) {
                ps.setString(1, orderCode);
                ps.setString(2, phone);
                ps.setBigDecimal(3, totalPrice);
                ps.executeUpdate();
            }


            try (PreparedStatement psItem = conn.prepareStatement(insertOrderItemSql);
                 PreparedStatement psUpdate = conn.prepareStatement(updateProductSql)) {

                for (OrderItem item : items) {


                    psUpdate.setInt(1, item.getQuantity());
                    psUpdate.setString(2, item.getProductCode());
                    psUpdate.setInt(3, item.getQuantity());

                    if (psUpdate.executeUpdate() == 0) {
                        throw new OrderException(
                                "Sản phẩm \"" + item.getProductName() + "\" không đủ tồn kho"
                        );
                    }


                    psItem.setString(1, orderCode);
                    psItem.setString(2, item.getProductCode());
                    psItem.setInt(3, item.getQuantity());
                    psItem.setBigDecimal(4, item.getPrice());
                    psItem.addBatch();
                }

                psItem.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement(updateBalanceSql)) {
                ps.setBigDecimal(1, totalPrice);
                ps.setString(2, phone);
                ps.executeUpdate();
            }

            conn.commit();

        } catch (OrderException e) {
            rollback(conn);
            throw e;

        } catch (Exception e) {
            rollback(conn);
            e.printStackTrace();
            throw new OrderException("Có lỗi xảy ra khi tạo đơn hàng. Vui lòng thử lại");

        } finally {
            close(conn);
        }
    }

    private void rollback(Connection conn) {
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ignored) {}
        }
    }

    private void close(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {}
        }
    }


}
