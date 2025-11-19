package com.luukien.javacard.state;

import com.luukien.javacard.model.OrderItem;
import com.luukien.javacard.model.Product;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;

import java.util.prefs.Preferences;

@Getter
public class AppState {
    @Getter
    private static final AppState instance = new AppState();
    public static final String IS_LOGIN = "isLogin";
    public static final String EMAIL = "email";
    public static final String ROLE = "role";
    private final Preferences prefs = Preferences.userNodeForPackage(AppState.class);

    private final ObservableList<OrderItem> currentOrders = FXCollections.observableArrayList();

    private String currentUserRole;
    private String currentUserEmail;

    @Setter
    @Getter
    private Product currentProduct;

    private AppState() {
    }


    public void addOrderItem(OrderItem item) {
        currentOrders.add(item);
    }

    public void clearOrders() {
        currentOrders.clear();
    }

    public void setCurrentUser(String email, String role) {
        this.currentUserEmail = email;
        this.currentUserRole = role;
        prefs.put(EMAIL, email);
        prefs.put(ROLE, role);
        prefs.putBoolean(IS_LOGIN, true);
    }

    public boolean isLogin() {
        return prefs.getBoolean(IS_LOGIN, false);
    }

    public String getPref(String key) {
        return prefs.get(key, "");
    }

    public void logout() {
        currentUserEmail = null;
        currentUserRole = null;
        clearOrders();
    }
}
