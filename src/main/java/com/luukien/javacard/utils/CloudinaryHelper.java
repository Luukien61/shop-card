package com.luukien.javacard.utils;

import com.cloudinary.Cloudinary;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CloudinaryHelper {
    private static final Cloudinary cloudinary;

    static {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", EnvLoader.get("CLOUD_NAME"));
        config.put("api_key", EnvLoader.get("API_KEY"));
        config.put("api_secret", EnvLoader.get("API_SECRET"));

        cloudinary = new Cloudinary(config);
    }

    public static String uploadImage(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            return null;
        }

        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", "image");


        try {
            Map uploadResult = cloudinary.uploader().upload(imageFile, options);

            String publicId = (String) uploadResult.get("public_id");
            return cloudinary.url().generate(publicId);
        } catch (IOException e) {
            System.err.println("Lỗi upload ảnh: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}