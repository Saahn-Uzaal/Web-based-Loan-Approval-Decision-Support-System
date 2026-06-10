package com.loanapproval.dss.loan;

import com.loanapproval.dss.loan.dto.LoanContractAcceptanceChallengeResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanContractAcceptanceCaptchaService {

    private static final String TOKEN_TYPE = "loan_contract_acceptance_captcha";

    private final SecretKey signingKey;
    private final long challengeExpirationMinutes;

    public LoanContractAcceptanceCaptchaService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.loan.contract-acceptance-captcha-expiration-minutes:5}") long challengeExpirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.challengeExpirationMinutes = challengeExpirationMinutes;
    }

    public LoanContractAcceptanceChallengeResponse generateChallenge(Long customerId, Long loanRequestId) {
        int leftOperand = ThreadLocalRandom.current().nextInt(11, 38);
        int rightOperand = ThreadLocalRandom.current().nextInt(2, 10);
        String operator = ThreadLocalRandom.current().nextBoolean() ? "+" : "-";
        if ("-".equals(operator) && rightOperand > leftOperand) {
            int swap = leftOperand;
            leftOperand = rightOperand;
            rightOperand = swap;
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(Math.max(challengeExpirationMinutes, 1), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject("loan-contract-acceptance-captcha")
                .claim("token_type", TOKEN_TYPE)
                .claim("customer_id", customerId)
                .claim("loan_request_id", loanRequestId)
                .claim("left_operand", leftOperand)
                .claim("right_operand", rightOperand)
                .claim("operator", operator)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();

        return new LoanContractAcceptanceChallengeResponse(
                "Để xác nhận là thao tác chủ động của bạn, vui lòng nhập kết quả phép tính: "
                        + leftOperand + " " + operator + " " + rightOperand,
                token,
                expiresAt);
    }

    public void validateChallenge(
            Long customerId,
            Long loanRequestId,
            String captchaToken,
            Integer captchaAnswer) {
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã CAPTCHA xác nhận hợp đồng");
        }
        if (captchaAnswer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập kết quả CAPTCHA trước khi chấp nhận hợp đồng");
        }

        Claims claims = parseClaims(captchaToken);
        String tokenType = claims.get("token_type", String.class);
        if (!TOKEN_TYPE.equals(tokenType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã CAPTCHA xác nhận hợp đồng không hợp lệ");
        }

        Long tokenCustomerId = toLongClaim(claims, "customer_id");
        Long tokenLoanRequestId = toLongClaim(claims, "loan_request_id");
        if (!Objects.equals(tokenCustomerId, customerId) || !Objects.equals(tokenLoanRequestId, loanRequestId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã CAPTCHA này không áp dụng cho hồ sơ vay hiện tại");
        }

        Integer leftOperand = claims.get("left_operand", Integer.class);
        Integer rightOperand = claims.get("right_operand", Integer.class);
        String operator = claims.get("operator", String.class);
        if (leftOperand == null || rightOperand == null || operator == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã CAPTCHA xác nhận hợp đồng không đầy đủ dữ liệu");
        }

        int expectedAnswer = switch (operator) {
            case "+" -> leftOperand + rightOperand;
            case "-" -> leftOperand - rightOperand;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã CAPTCHA xác nhận hợp đồng có phép tính không được hỗ trợ");
        };

        if (captchaAnswer.intValue() != expectedAnswer) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kết quả CAPTCHA không chính xác");
        }
    }

    private Claims parseClaims(String captchaToken) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(captchaToken)
                    .getPayload();
        } catch (JwtException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mã CAPTCHA đã hết hạn hoặc không hợp lệ. Vui lòng tải mã mới.",
                    ex);
        }
    }

    private Long toLongClaim(Claims claims, String claimName) {
        Number value = claims.get(claimName, Number.class);
        return value != null ? value.longValue() : null;
    }
}
