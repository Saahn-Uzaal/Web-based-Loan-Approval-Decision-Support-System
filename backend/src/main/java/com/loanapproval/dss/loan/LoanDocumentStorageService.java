package com.loanapproval.dss.loan;

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
public class LoanDocumentStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> SUPPLEMENTAL_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp", "pdf", "doc", "docx", "xls", "xlsx");

    private final Path storageRoot;

    public LoanDocumentStorageService(
            @Value("${app.storage.loan-documents-path:./storage/loan-documents}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    public StoredLoanDocument store(Long loanRequestId, LoanDocumentType documentType, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng tải lên chứng từ hợp lệ");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chứng từ không được vượt quá 10MB");
        }

        String originalFileName = sanitizeFileName(file.getOriginalFilename(), documentType);
        String extension = extractExtension(originalFileName);
        if (!allowedExtensionsFor(documentType).contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    documentType == LoanDocumentType.SUPPLEMENTAL_DOCUMENT
                            ? "Giấy tờ bổ sung chỉ chấp nhận JPG, JPEG, PNG, WEBP, PDF, DOC, DOCX, XLS, XLSX"
                            : "Chỉ chấp nhận ảnh JPG, JPEG, PNG hoặc WEBP cho chứng từ hồ sơ vay");
        }

        Path loanDirectory = storageRoot.resolve(String.valueOf(loanRequestId)).normalize();
        ensureWithinRoot(loanDirectory);

        String storageName = documentType.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "." + extension;
        Path targetFile = loanDirectory.resolve(storageName).normalize();
        ensureWithinRoot(targetFile);

        try {
            Files.createDirectories(loanDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu chứng từ hồ sơ vay", ex);
        }

        return new StoredLoanDocument(
                originalFileName,
                storageName,
                resolveContentType(file.getContentType(), extension),
                file.getSize(),
                Instant.now());
    }

    public LoanDocumentDownload load(LoanDocumentRecord document) {
        Path loanDirectory = storageRoot.resolve(String.valueOf(document.loanRequestId())).normalize();
        Path filePath = loanDirectory.resolve(document.storageName()).normalize();
        ensureWithinRoot(filePath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file chứng từ hồ sơ vay");
        }

        return new LoanDocumentDownload(
                new FileSystemResource(filePath),
                document.originalFileName(),
                document.contentType(),
                document.fileSize() != null ? document.fileSize() : 0L);
    }

    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn file không hợp lệ");
        }
    }

    private String sanitizeFileName(String fileName, LoanDocumentType documentType) {
        String candidate = StringUtils.hasText(fileName)
                ? fileName
                : documentType.name().toLowerCase(Locale.ROOT) + ".jpg";
        String sanitized = Path.of(candidate).getFileName().toString().trim();
        if (sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file chứng từ không hợp lệ");
        }
        return sanitized;
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chứng từ hồ sơ vay phải có phần mở rộng JPG, JPEG, PNG hoặc WEBP");
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
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    private Set<String> allowedExtensionsFor(LoanDocumentType documentType) {
        return documentType == LoanDocumentType.SUPPLEMENTAL_DOCUMENT ? SUPPLEMENTAL_EXTENSIONS : IMAGE_EXTENSIONS;
    }

    public record StoredLoanDocument(
            String originalFileName,
            String storageName,
            String contentType,
            Long fileSize,
            Instant uploadedAt) {
    }
}
