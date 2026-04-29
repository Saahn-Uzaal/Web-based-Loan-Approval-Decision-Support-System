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
public class CustomerPayslipStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx");

    private final Path storageRoot;

    public CustomerPayslipStorageService(
        @Value("${app.storage.customer-payslips-path:./storage/profile-documents}") String storagePath
    ) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredPayslip store(Long userId, MultipartFile file, String previousStorageName) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng tải lên phiếu lương hợp lệ");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phiếu lương không được vượt quá 10MB");
        }

        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Chỉ chấp nhận file phiếu lương định dạng PDF, Word hoặc Excel"
            );
        }

        Path customerDirectory = storageRoot.resolve(String.valueOf(userId)).normalize();
        ensureWithinRoot(customerDirectory);

        String storageName = UUID.randomUUID() + "." + extension;
        Path targetFile = customerDirectory.resolve(storageName).normalize();
        ensureWithinRoot(targetFile);

        try {
            Files.createDirectories(customerDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            deletePreviousFile(customerDirectory, previousStorageName);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu phiếu lương", ex);
        }

        return new StoredPayslip(
            originalFileName,
            storageName,
            resolveContentType(file.getContentType(), extension),
            file.getSize(),
            Instant.now()
        );
    }

    public CustomerPayslipDownload load(CustomerProfile profile) {
        if (profile.payslipStorageName() == null || profile.payslipStorageName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Khách hàng chưa nộp phiếu lương");
        }

        Path customerDirectory = storageRoot.resolve(String.valueOf(profile.userId())).normalize();
        Path filePath = customerDirectory.resolve(profile.payslipStorageName()).normalize();
        ensureWithinRoot(filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file phiếu lương");
        }

        return new CustomerPayslipDownload(
            new FileSystemResource(filePath),
            profile.payslipOriginalFilename(),
            profile.payslipContentType(),
            profile.payslipFileSize() != null ? profile.payslipFileSize() : 0L
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
        String candidate = StringUtils.hasText(fileName) ? fileName : "payslip";
        String sanitized = Path.of(candidate).getFileName().toString().trim();
        if (sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file phiếu lương không hợp lệ");
        }
        return sanitized;
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Phiếu lương phải có phần mở rộng thuộc PDF, Word hoặc Excel"
            );
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String contentType, String extension) {
        if (StringUtils.hasText(contentType) && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    public record StoredPayslip(
        String originalFileName,
        String storageName,
        String contentType,
        Long fileSize,
        Instant uploadedAt
    ) {
    }
}
