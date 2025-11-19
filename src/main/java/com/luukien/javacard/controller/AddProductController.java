package com.luukien.javacard.controller;

import com.luukien.javacard.screen.SceneManager;
import com.luukien.javacard.screen.Scenes;
import com.luukien.javacard.sql.SqlQueries;
import com.luukien.javacard.utils.ApplicationHelper;
import com.luukien.javacard.utils.CurrencyConverter;
import com.luukien.javacard.utils.DatabaseHelper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import static com.luukien.javacard.utils.ApplicationHelper.TRY_AGAIN_MESSAGE;

public class AddProductController {

    @FXML
    private TextField nameTextfield;
    @FXML
    private TextField codeTextfield;
    @FXML
    private TextField remainTextfield;
    @FXML
    private TextField priceTextfield;
    @FXML
    private Button cancelBtn;
    @FXML
    private Button addBtn;


    @FXML
    private void initialize() {
        cancelBtn.setOnAction(e -> onCancelBtnClick());
        addBtn.setOnAction(e -> onAddBtnClick());
    }


    private void onCancelBtnClick() {
        SceneManager.switchTo(Scenes.HOME_MANAGEMENT_SCENE);
    }

    private void onAddBtnClick() {
        String productName = nameTextfield.getText().trim();
        BigDecimal price = CurrencyConverter.getNumericValue(priceTextfield);
        String productCode = codeTextfield.getText();
        String quantity = remainTextfield.getText().trim();

        if (productName.isBlank() || quantity.isBlank() || Objects.equals(price, BigDecimal.ZERO)) {
            ApplicationHelper.showAlert("Vui lòng nhập đầy đủ thông tin.", true);
            return;
        }

        try (Connection conn = DatabaseHelper.getConnection()) {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(SqlQueries.getInstance().getADD_PRODUCT())) {
                    if (productCode == null || productCode.isBlank()) {
                        productCode = generateProductCode(productName);
                    }
                    ps.setString(1, productCode);
                    ps.setString(2, productName);
                    ps.setInt(3, Integer.parseInt(quantity));
                    ps.setBigDecimal(4, price);
                    ps.executeUpdate();
                    ps.close();
                    clearTextField();
                    ApplicationHelper.showAlert("Thêm thành công!", false);

                }
            } else throw new RuntimeException("Unable to connect to the database");
        } catch (Exception e) {
            ApplicationHelper.showAlert(TRY_AGAIN_MESSAGE, true);
        }

    }

    private void clearTextField() {
        nameTextfield.clear();
        codeTextfield.clear();
        remainTextfield.clear();
        priceTextfield.clear();
    }

    private static String generateProductCode(String productName) {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }

        String[] words = productName.trim().split("\\s+");
        StringBuilder prefix = new StringBuilder();
        for (String w : words) {
            prefix.append(Character.toUpperCase(w.charAt(0)));
        }

        String timePart = new SimpleDateFormat("yyMMddHHmmss").format(new Date());

        String code = prefix + timePart;

        if (code.length() > 15) {
            code = code.substring(code.length() - 15);
        }

        return code;
    }

}
