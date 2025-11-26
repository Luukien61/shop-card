module com.luukien.javacard {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.sql;
    requires static lombok;
    requires bcrypt;
    requires java.prefs;
    requires java.smartcardio;
    requires java.desktop;
    requires cloudinary.core;
    requires org.bouncycastle.provider;
    requires jakarta.mail;

    opens com.luukien.javacard to javafx.fxml;
    exports com.luukien.javacard;
    exports com.luukien.javacard.controller;
    opens com.luukien.javacard.controller to javafx.fxml;
    opens com.luukien.javacard.model to javafx.base;
}