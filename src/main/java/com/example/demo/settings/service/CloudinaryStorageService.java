package com.example.demo.settings.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// CloudinaryStorageService.java

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryStorageService {

    private final Cloudinary cloudinary;

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp");

    // ══════════════════════════════════════════
    // SUBIR LOGO DE TENANT
    // ══════════════════════════════════════════

    public String uploadTenantLogo(MultipartFile file, Long tenantId) {

        validateImage(file);

        try {
            // Nombre público único para evitar colisiones
            String publicId = "tenants/logos/tenant_"
                    + tenantId + "_"
                    + System.currentTimeMillis();

            Map<String, Object> params = Map.of(
                    "public_id", publicId,
                    "folder", "pos-system", // carpeta en Cloudinary
                    "overwrite", true,
                    "resource_type", "image",
                    "transformation", new Transformation()
                            .width(400)
                            .height(400)
                            .crop("limit") // no agranda, solo reduce
                            .quality("auto") // compresión automática
                            .fetchFormat("auto") // formato óptimo (webp si soporta)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader()
                    .upload(file.getBytes(), params);

            // Cloudinary devuelve "secure_url" con HTTPS
            String secureUrl = (String) result.get("secure_url");

            log.info("Logo subido a Cloudinary: {}", secureUrl);

            return secureUrl;

        } catch (IOException e) {
            throw new RuntimeException("Error al subir imagen a Cloudinary", e);
        }
    }

    // ══════════════════════════════════════════
    // BORRAR IMAGEN ANTERIOR
    // ══════════════════════════════════════════

    public void deleteIfExists(String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank())
            return;

        // Si es una URL local antigua (/uploads/...), no intentar borrar en Cloudinary
        if (!imageUrl.contains("cloudinary"))
            return;

        try {
            String publicId = extractPublicId(imageUrl);

            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, Map.of(
                        "resource_type", "image"));
                log.info("Logo anterior eliminado de Cloudinary: {}", publicId);
            }

        } catch (Exception e) {
            // Loguear pero no romper la operación principal
            log.warn("No se pudo eliminar logo anterior de Cloudinary: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════
    // VALIDACIÓN ROBUSTA
    // ══════════════════════════════════════════

    private void validateImage(MultipartFile file) {

        // 1. Archivo no vacío
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar una imagen");
        }

        // 2. Content type permitido
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Formato no permitido. Usa PNG, JPG o WEBP");
        }

        // 3. Tamaño máximo
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "La imagen no debe exceder 2MB");
        }

        // 4. Verificar que realmente sea una imagen válida
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new IllegalArgumentException(
                        "El archivo no es una imagen válida");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error al validar la imagen", e);
        }
    }

    // ══════════════════════════════════════════
    // EXTRAER PUBLIC ID DE URL CLOUDINARY
    // ══════════════════════════════════════════

    /**
     * De una URL como:
     * https://res.cloudinary.com/xxx/image/upload/v123/pos-system/tenants/logos/tenant_1_171234.jpg
     * extrae: pos-system/tenants/logos/tenant_1_171234
     */
    private String extractPublicId(String url) {
        try {
            // Buscar después de "/upload/vXXXX/"
            String marker = "/upload/";
            int uploadIdx = url.indexOf(marker);
            if (uploadIdx == -1)
                return null;

            String afterUpload = url.substring(uploadIdx + marker.length());

            // Saltar el version tag "v12345/"
            if (afterUpload.startsWith("v")) {
                int slashIdx = afterUpload.indexOf('/');
                if (slashIdx != -1) {
                    afterUpload = afterUpload.substring(slashIdx + 1);
                }
            }

            // Quitar extensión
            int dotIdx = afterUpload.lastIndexOf('.');
            if (dotIdx > 0) {
                afterUpload = afterUpload.substring(0, dotIdx);
            }

            return afterUpload;

        } catch (Exception e) {
            log.warn("No se pudo extraer publicId de: {}", url);
            return null;
        }
    }
}
