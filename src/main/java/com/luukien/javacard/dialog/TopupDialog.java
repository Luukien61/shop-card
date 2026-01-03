package com.luukien.javacard.dialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.util.Optional;

public class TopupDialog {

    public static Optional<BigDecimal> show() {
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Nạp tiền");
        dialog.setHeaderText("Nhập số tiền cần nạp (VND):");

        TextField amountField = new TextField();
        amountField.setPromptText("Ví dụ: 1000000");

        VBox vbox = new VBox(10, new Label("Số tiền:"), amountField);
        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);

        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    BigDecimal amount = new BigDecimal(amountField.getText().trim());
                    if (amount.compareTo(BigDecimal.ZERO) > 0) {
                        return amount;
                    }
                } catch (NumberFormatException ignored) {}
            }
            return null;
        });

        return dialog.showAndWait();
    }
}
