package com.luukien.javacard.service;

import com.luukien.javacard.exception.OrderException;
import com.luukien.javacard.model.OrderItem;
import com.luukien.javacard.model.UserCardInfo;
import com.luukien.javacard.utils.CardHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import lombok.Getter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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

            // 5️⃣ Insert order
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
