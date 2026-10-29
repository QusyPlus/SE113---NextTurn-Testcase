package com.nextturn.lab3;

import com.nextturn.constant.ValkeyKeyConstant;
import com.nextturn.exception.auth.AuthErrorCode;
import com.nextturn.exception.auth.AuthException;
import com.nextturn.service.auth.OtpService;
import com.nextturn.service.notification.EmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * U12 – Đối soát mã OTP (Valkey và bộ nhớ dự phòng) (REQ-AUTH-07).
 * Mã được kiểm thử: com.nextturn.service.auth.OtpService#verifyOtp (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U12.
 */
@DisplayName("U12 - OtpService.verifyOtp(contact, rawOtp)")
class OtpServiceVerifyOtpUtcTest {

    private static final String CONTACT = "khach@pkalpha.vn";
    private static final String CODE_KEY = ValkeyKeyConstant.otpCodeKey(CONTACT);
    private static final String ATTEMPTS_KEY = ValkeyKeyConstant.otpAttemptsKey(CONTACT);

    private EmailService emailService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private OtpService memory;
    private OtpService valkey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emailService = mock(EmailService.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        memory = new OtpService(null, emailService, 300, 3, 60, 5);
        valkey = new OtpService(redis, emailService, 300, 3, 60, 5);
    }

    /** Gửi mã qua bộ nhớ dự phòng và lấy lại mã 6 chữ số từ EmailService giả lập. */
    private String issueCode(OtpService service, String contact) {
        service.generateAndSendOtp(contact, false);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendOtpEmail(anyString(), code.capture(), anyLong());
        return code.getValue();
    }

    private static String wrongCode(String code) {
        return "000000".equals(code) ? "111111" : "000000";
    }

    private static AuthException assertAuthError(AuthErrorCode expected, Executable call) {
        AuthException ex = assertThrows(AuthException.class, call);
        assertEquals(expected.code(), ex.getCode());
        assertEquals(expected.httpStatus(), ex.getHttpStatus());
        return ex;
    }

    @Test
    @DisplayName("UTCID01 [A] contact null")
    void utcid01_contactNull() {
        assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp(null, "123456"));
    }

    @Test
    @DisplayName("UTCID02 [B] Mã chỉ có khoảng trắng")
    void utcid02_maChiCoKhoangTrang() {
        assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp("khach@pkalpha.vn", "   "));
    }

    @Test
    @DisplayName("UTCID03 [A] Chưa từng gửi mã")
    void utcid03_chuaTungGuiMa() {
        AuthException ex = assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp("khach@pkalpha.vn", "123456"));
        assertTrue(ex.getMessage().contains("No active verification code"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID04 [B] Mã đã hết hạn")
    void utcid04_maDaHetHan() {
        OtpService expired = new OtpService(null, emailService, -1, 3, 60, 5);
        String code = issueCode(expired, CONTACT);
        assertAuthError(AuthErrorCode.OTP_EXPIRED, () -> expired.verifyOtp(CONTACT, code));
    }

    @Test
    @DisplayName("UTCID05 [N] Nhập đúng; dùng lại lần 2 bị từ chối")
    void utcid05_nhapDungDungLaiLan2BiTu() {
        String code = issueCode(memory, CONTACT);
        String wrong = wrongCode(code);
        assertDoesNotThrow(() -> memory.verifyOtp(CONTACT, code));
        AuthException replay = assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp(CONTACT, code));
        assertTrue(replay.getMessage().contains("No active verification code"));
    }

    @Test
    @DisplayName("UTCID06 [N] Sai lần 1")
    void utcid06_saiLan1() {
        String code = issueCode(memory, CONTACT);
        String wrong = wrongCode(code);
        AuthException ex = assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp(CONTACT, wrong));
        assertTrue(ex.getMessage().contains("Attempts remaining: 2"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID07 [B] Sai lần 2")
    void utcid07_saiLan2() {
        String code = issueCode(memory, CONTACT);
        String wrong = wrongCode(code);
        assertThrows(AuthException.class, () -> memory.verifyOtp(CONTACT, wrong));
        AuthException ex = assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp(CONTACT, wrong));
        assertTrue(ex.getMessage().contains("Attempts remaining: 1"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID08 [B] Sai lần 3 → hủy mã (mã đúng sau đó cũng bị từ chối)")
    void utcid08_saiLan3HuyMaMaDungSau() {
        String code = issueCode(memory, CONTACT);
        String wrong = wrongCode(code);
        assertThrows(AuthException.class, () -> memory.verifyOtp(CONTACT, wrong));
        assertThrows(AuthException.class, () -> memory.verifyOtp(CONTACT, wrong));
        assertAuthError(AuthErrorCode.OTP_TOO_MANY_ATTEMPTS, () -> memory.verifyOtp(CONTACT, wrong));
        assertAuthError(AuthErrorCode.INVALID_OTP, () -> memory.verifyOtp(CONTACT, code));
    }

    @Test
    @DisplayName("UTCID09 [B] Chuẩn hóa hoa thường/khoảng trắng của contact và mã")
    void utcid09_chuanHoaHoaThuongKhoangTrangCuaContact() {
        String code = issueCode(memory, CONTACT);
        String wrong = wrongCode(code);
        assertDoesNotThrow(() -> memory.verifyOtp(" Khach@PKAlpha.vn ", " " + code + " "));
    }

    @Test
    @DisplayName("UTCID10 [A] Valkey không có khóa mã")
    void utcid10_valkeyKhongCoKhoaMa() {
        when(ops.get(CODE_KEY)).thenReturn(null);
        AuthException ex = assertAuthError(AuthErrorCode.INVALID_OTP, () -> valkey.verifyOtp(CONTACT, "482913"));
        assertTrue(ex.getMessage().contains("No active verification code"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID11 [A] Valkey lỗi kết nối → dùng bộ nhớ dự phòng")
    void utcid11_valkeyLoiKetNoiDungBoNhoDu() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("Valkey connection refused"));
        AuthException ex = assertAuthError(AuthErrorCode.INVALID_OTP, () -> valkey.verifyOtp(CONTACT, "482913"));
        assertTrue(ex.getMessage().contains("No active verification code"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID12 [A] Valkey: attempts đã là 3")
    void utcid12_valkeyAttemptsDaLa3() {
        when(ops.get(CODE_KEY)).thenReturn("482913");
        when(ops.get(ATTEMPTS_KEY)).thenReturn("3");
        assertAuthError(AuthErrorCode.OTP_TOO_MANY_ATTEMPTS, () -> valkey.verifyOtp(CONTACT, "482913"));
        verify(redis).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
    }

    @Test
    @DisplayName("UTCID13 [B] Valkey: sai lần thứ 3")
    void utcid13_valkeySaiLanThu3() {
        when(ops.get(CODE_KEY)).thenReturn("482913");
        when(ops.get(ATTEMPTS_KEY)).thenReturn("2");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(3L);
        assertAuthError(AuthErrorCode.OTP_TOO_MANY_ATTEMPTS, () -> valkey.verifyOtp(CONTACT, "000000"));
        verify(redis).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
    }

    @Test
    @DisplayName("UTCID14 [N] Valkey: sai lần 1")
    void utcid14_valkeySaiLan1() {
        when(ops.get(CODE_KEY)).thenReturn("482913");
        when(ops.get(ATTEMPTS_KEY)).thenReturn("0");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(1L);
        AuthException ex = assertAuthError(AuthErrorCode.INVALID_OTP, () -> valkey.verifyOtp(CONTACT, "000000"));
        assertTrue(ex.getMessage().contains("Attempts remaining: 2"), ex.getMessage());
    }

    @Test
    @DisplayName("UTCID15 [N] Valkey: nhập đúng → xóa mã và bộ đếm")
    void utcid15_valkeyNhapDungXoaMaVaBoDem() {
        when(ops.get(CODE_KEY)).thenReturn("482913");
        when(ops.get(ATTEMPTS_KEY)).thenReturn("0");
        assertDoesNotThrow(() -> valkey.verifyOtp(CONTACT, "482913"));
        verify(redis).delete(List.of(CODE_KEY, ATTEMPTS_KEY));
    }
}
