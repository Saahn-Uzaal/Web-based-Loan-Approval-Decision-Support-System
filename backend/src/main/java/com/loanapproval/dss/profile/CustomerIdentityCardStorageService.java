package com.loanapproval.dss.profile;

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
public class CustomerIdentityCardStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path storageRoot;

    public CustomerIdentityCardStorageService(
        @Value("${app.storage.customer-payslips-path:./storage/profile-documents}") String storagePath
    ) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredIdentityCard store(
        Long userId,
        CustomerIdentityCardSide side,
        MultipartFile file,
        String previousStorageName
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng tải ảnh CCCD hợp lệ");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh CCCD không được vượt quá 10MB");
        }

        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Chỉ chấp nhận ảnh CCCD định dạng JPG, JPEG, PNG hoặc WEBP"
            );
        }

        Path customerDirectory = storageRoot.resolve(String.valueOf(userId)).resolve("identity-card").normalize();
        ensureWithinRoot(customerDirectory);

        String storageName = side.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "." + extension;
        Path targetFile = customerDirectory.resolve(storageName).normalize();
        ensureWithinRoot(targetFile);

        try {
            Files.createDirectories(customerDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            deletePreviousFile(customerDirectory, previousStorageName);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu ảnh CCCD", ex);
        }

        return new StoredIdentityCard(
            originalFileName,
            storageName,
            resolveContentType(file.getContentType(), extension),
            file.getSize(),
            Instant.now()
        );
    }

    public CustomerIdentityCardDownload load(CustomerProfile profile, CustomerIdentityCardSide side) {
        String storageName = side == CustomerIdentityCardSide.FRONT
            ? profile.identityCardFrontStorageName()
            : profile.identityCardBackStorageName();
        String originalFileName = side == CustomerIdentityCardSide.FRONT
            ? profile.identityCardFrontOriginalFilename()
            : profile.identityCardBackOriginalFilename();
        String contentType = side == CustomerIdentityCardSide.FRONT
            ? profile.identityCardFrontContentType()
            : profile.identityCardBackContentType();
        Long fileSize = side == CustomerIdentityCardSide.FRONT
            ? profile.identityCardFrontFileSize()
            : profile.identityCardBackFileSize();

        if (storageName == null || storageName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Khách hàng chưa nộp đủ ảnh CCCD");
        }

        Path customerDirectory = storageRoot.resolve(String.valueOf(profile.userId())).resolve("identity-card").normalize();
        Path filePath = customerDirectory.resolve(storageName).normalize();
        ensureWithinRoot(filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh CCCD");
        }

        return new CustomerIdentityCardDownload(
            new FileSystemResource(filePath),
            originalFileName,
            contentType,
            fileSize != null ? fileSize : 0L
        );
    }

    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn file không hợp lệ");
        }
    }

    private void deletePreviousFile(Path customerDirectory, String previousStorageName) throws IOException {
        if (!StringUtils.hasText(previousStorageName)) {
            return;
        }
        Path previousFile = customerDirectory.resolve(previousStorageName).normalize();
        if (!previousFile.startsWith(customerDirectory)) {
            return;
        }
        Files.deleteIfExists(previousFile);
    }

    private String sanitizeFileName(String fileName) {
        String candidate = StringUtils.hasText(fileName) ? fileName : "identity-card";
        String sanitized = Path.of(candidate).getFileName().toString().trim();
        if (sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file CCCD không hợp lệ");
        }
        return sanitized;
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Ảnh CCCD phải có phần mở rộng JPG, JPEG, PNG hoặc WEBP"
            );
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

    public record StoredIdentityCard(
        String originalFileName,
        String storageName,
        String contentType,
        Long fileSize,
        Instant uploadedAt
    ) {
    }
}
