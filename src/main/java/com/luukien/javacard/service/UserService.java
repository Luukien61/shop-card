package com.luukien.javacard.service;

import com.luukien.javacard.model.User;
import com.luukien.javacard.sql.SqlQueries;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.DatabaseHelper;
import lombok.Getter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserService {
    @Getter
    private static final UserService instance = new UserService();


    public List<User> loadUsers() {
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn == null) throw new RuntimeException(ApplicationHelper.CONN_DB_MESSAGE);
            try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getGET_PREVIEW_USERS())) {
                return getPreviewUser(users, ps);
            }
        } catch (Exception e) {
            ApplicationHelper.showAlert(e.getMessage(), true);
        }
        return users;
    }

    private List<User> getPreviewUser(List<User> items, PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String name = rs.getString("user_name");
            String address = rs.getString("address");
            String phone = rs.getString("phone");
            String memberTier = rs.getString("member_tier");
            User user = new User(name, address, phone, memberTier);
            items.add(user);
        }
        return items;
    }
}
