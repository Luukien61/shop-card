package com.luukien.javacard.controller;

import com.luukien.javacard.utils.CardHelper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;

public class InitiateCardController {

    @FXML
    private TextField usernameField;
    @FXML
    private DatePicker dateField;
    @FXML
    private RadioButton menGender;
    @FXML
    private ToggleGroup gender;
    @FXML
    private RadioButton womenGender;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private ImageView imageView;
    @FXML
    private Button chooseImageBtn;
    @FXML
    private Button finishBtn;

    private File selectedImageFile = null;
    private byte[] selectedImageBytes;


    @FXML
    public void initialize() {
        chooseImageBtn.setOnAction(e -> onChooseImage());
        finishBtn.setOnAction(e -> onFinish());
    }


    @FXML
    private void onChooseImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            selectedImageFile = file;
            Image image = new Image(file.toURI().toString());
            imageView.setImage(image);
            try {
                BufferedImage original = ImageIO.read(file);
                BufferedImage resized = resize(original, 200, 200);
                selectedImageBytes = compressImage(resized, 0.6f);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private byte[] compressImage(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality); // 0.0f → thấp nhất, 1.0f → tốt nhất
        }

        writer.setOutput(new MemoryCacheImageOutputStream(baos));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();

        return baos.toByteArray();
    }

    private BufferedImage resize(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resizedImage;
    }


    @FXML
    private void onFinish() {

        String username = usernameField.getText();
        LocalDate birthDate = dateField.getValue();
        String phone = phoneField.getText();
        String address = addressField.getText();


        String genderSelected = "";
        if (menGender.isSelected()) {
            genderSelected = "Nam";
        } else if (womenGender.isSelected()) {
            genderSelected = "Nữ";
        }

        String imagePath = selectedImageFile != null ? selectedImageFile.getAbsolutePath() : null;

        CardHelper.initiateCard(username, address, phone, "123456", "123456", selectedImageFile);


    }


    private byte[] fileToByteArray(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
