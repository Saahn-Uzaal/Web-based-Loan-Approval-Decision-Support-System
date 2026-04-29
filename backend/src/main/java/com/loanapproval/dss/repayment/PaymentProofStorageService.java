package com.loanapproval.dss.repayment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentProofStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path storageRoot;

    public PaymentProofStorageService(
            @Value("${app.storage.payment-proofs-path:./storage/payment-proofs}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredPaymentProof store(Long customerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng tải lên ảnh bill chuyển khoản hợp lệ");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh bill chuyển khoản không được vượt quá 10MB");
        }

        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ chấp nhận ảnh JPG, JPEG, PNG hoặc WEBP cho bill chuyển khoản");
        }

        Path customerDirectory = storageRoot.resolve(String.valueOf(customerId)).normalize();
        ensureWithinRoot(customerDirectory);

        String storageName = "payment-proof-" + UUID.randomUUID() + "." + extension;
        Path targetFile = customerDirectory.resolve(storageName).normalize();
        ensureWithinRoot(targetFile);

        try {
            Files.createDirectories(customerDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh bill chuyển khoản", ex);
        }

        return new StoredPaymentProof(
                originalFileName,
                storageName,
                resolveContentType(file.getContentType(), extension),
                file.getSize(),
                Instant.now());
    }

    public PaymentProofDownload load(PaymentConfirmationRequestRecord record) {
        Path customerDirectory = storageRoot.resolve(String.valueOf(record.customerId())).normalize();
        Path filePath = customerDirectory.resolve(record.proofStorageName()).normalize();
        ensureWithinRoot(filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh bill chuyển khoản");
        }

        return new PaymentProofDownload(
                new FileSystemResource(filePath),
                record.proofOriginalFileName(),
                record.proofContentType(),
                record.proofFileSize() != null ? record.proofFileSize() : 0L);
    }

    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn lưu bill chuyển khoản không hợp lệ");
        }
    }

    private String sanitizeFileName(String fileName) {
        String candidate = StringUtils.hasText(fileName) ? fileName : "payment-proof.jpg";
        String sanitized = Path.of(candidate).getFileName().toString().trim();
        if (sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file bill chuyển khoản không hợp lệ");
        }
        return sanitized;
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ảnh bill chuyển khoản phải có phần mở rộng JPG, JPEG, PNG hoặc WEBP");
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String contentType, String extension) {
        if (StringUtils.hasText(contentType) && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    public record StoredPaymentProof(
            String originalFileName,
            String storageName,
            String contentType,
            Long fileSize,
            Instant uploadedAt) {
    }
}

