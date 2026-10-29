package com.nextturn.lab3;

import com.nextturn.dto.auth.TenantAuthRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lab 1 – F12-004 (tự động hóa ở mức DTO): đối chiếu ràng buộc Bean Validation của {@link TenantAuthRequest}
 * với REQ-AUTH-01 của SRS: "Mã cơ sở không được để trống, độ dài từ 3 đến 30 ký tự,
 * chỉ bao gồm chữ cái in thường, chữ số và dấu gạch nối".
 * Các ca SRS05–SRS08 dự kiến FAIL trên mã hiện tại (DF-AUTH-01).
 */
@DisplayName("F12-004 - TenantAuthRequest: mã cơ sở theo REQ-AUTH-01")
class TenantAuthRequestSrsConformanceTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static boolean accepted(String tenantCode) {
        Set<ConstraintViolation<TenantAuthRequest>> violations =
                validator.validate(TenantAuthRequest.builder().tenantCode(tenantCode).build());
        return violations.isEmpty();
    }

    @Test
    @DisplayName("SRS01 [N] Mã hợp lệ pk-alpha được chấp nhận")
    void srs01_validCode() {
        assertTrue(accepted("pk-alpha"));
    }

    @Test
    @DisplayName("SRS02 [B] 3 ký tự (biên dưới) được chấp nhận")
    void srs02_threeCharacters() {
        assertTrue(accepted("abc"));
    }

    @Test
    @DisplayName("SRS03 [B] 30 ký tự (biên trên) được chấp nhận")
    void srs03_thirtyCharacters() {
        assertTrue(accepted("a".repeat(30)));
    }

    @Test
    @DisplayName("SRS04 [B] Mã rỗng bị từ chối")
    void srs04_emptyCode() {
        assertFalse(accepted(""));
    }

    @Test
    @DisplayName("SRS05 [B] 2 ký tự bị từ chối")
    void srs05_twoCharacters() {
        assertFalse(accepted("ab"), "SRS: tối thiểu 3 ký tự, mã hiện tại cho phép từ 2 ký tự");
    }

    @Test
    @DisplayName("SRS06 [B] 31 ký tự bị từ chối")
    void srs06_thirtyOneCharacters() {
        assertFalse(accepted("a".repeat(31)), "SRS: tối đa 30 ký tự, mã hiện tại cho phép đến 50 ký tự");
    }

    @Test
    @DisplayName("SRS07 [A] Chữ in hoa bị từ chối")
    void srs07_uppercase() {
        assertFalse(accepted("PK-ALPHA"), "SRS: chỉ chữ thường, mã hiện tại cho phép chữ hoa");
    }

    @Test
    @DisplayName("SRS08 [A] Dấu gạch dưới bị từ chối")
    void srs08_underscore() {
        assertFalse(accepted("pk_alpha"), "SRS: chỉ chữ thường, chữ số, dấu gạch nối; mã hiện tại cho phép dấu gạch dưới");
    }

    @Test
    @DisplayName("SRS09 [A] Khoảng trắng bị từ chối")
    void srs09_whitespace() {
        assertFalse(accepted("pk alpha"));
    }

    @Test
    @DisplayName("SRS10 [A] Tiếng Việt có dấu bị từ chối")
    void srs10_vietnameseDiacritics() {
        assertFalse(accepted("bv-tâm-đức"));
    }
}
