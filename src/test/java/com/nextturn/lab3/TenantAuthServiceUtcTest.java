package com.nextturn.lab3;

import com.nextturn.dto.auth.TenantAuthRequest;
import com.nextturn.dto.auth.TenantAuthResponse;
import com.nextturn.entity.CentralTenant;
import com.nextturn.enums.TenantStatus;
import com.nextturn.enums.UserRole;
import com.nextturn.exception.auth.AuthErrorCode;
import com.nextturn.exception.auth.AuthException;
import com.nextturn.helper.KeycloakAuthHelper;
import com.nextturn.repository.auth.CentralTenantRepository;
import com.nextturn.service.auth.TenantAuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * U10 – Nhận diện cơ sở theo mã và tạo đường dẫn đăng nhập nhân sự (REQ-AUTH-01).
 * Mã được kiểm thử: com.nextturn.service.auth.TenantAuthService#authenticateTenant (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U10.
 */
@DisplayName("U10 - TenantAuthService.authenticateTenant(request)")
class TenantAuthServiceUtcTest {

    private static final String LOGIN_URL = "https://auth.nextturn.vn/login/pk-alpha";

    private CentralTenantRepository tenantRepository;
    private TenantAuthService service;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(CentralTenantRepository.class);
        KeycloakAuthHelper keycloakAuthHelper = mock(KeycloakAuthHelper.class);
        when(keycloakAuthHelper.buildStaffLoginUrl(any(CentralTenant.class))).thenReturn(LOGIN_URL);
        service = new TenantAuthService(tenantRepository, keycloakAuthHelper, null);
    }

    private static CentralTenant tenant(TenantStatus status) {
        return CentralTenant.builder().id("tenant-001").tenantCode("pk-alpha").tenantName("Phòng khám Alpha")
                .status(status).organisationId("org-001").build();
    }

    private static AuthException assertAuthError(AuthErrorCode expected, Executable call) {
        AuthException ex = assertThrows(AuthException.class, call);
        assertEquals(expected.code(), ex.getCode());
        assertEquals(expected.httpStatus(), ex.getHttpStatus());
        return ex;
    }

    @Test
    @DisplayName("UTCID01 [N] Mã hợp lệ, cơ sở ACTIVE")
    void utcid01_maHopLeCoSoActive() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha")).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE)));
        TenantAuthResponse response = service.authenticateTenant(TenantAuthRequest.builder().tenantCode("pk-alpha").build());
        assertEquals("pk-alpha", response.getTenantCode());
        assertEquals(LOGIN_URL, response.getLoginUrl());
        assertEquals(List.of(UserRole.TENANT_ADMIN_VALUE, UserRole.COUNTER_STAFF_VALUE), response.getAllowedRoles());
        verify(tenantRepository).findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha");
    }

    @Test
    @DisplayName("UTCID02 [A] Request null")
    void utcid02_requestNull() {
        assertAuthError(AuthErrorCode.TENANT_CODE_REQUIRED, () -> service.authenticateTenant(null));
    }

    @Test
    @DisplayName("UTCID03 [A] tenantCode null")
    void utcid03_tenantcodeNull() {
        assertAuthError(AuthErrorCode.TENANT_CODE_REQUIRED, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode(null).build()));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("UTCID04 [B] tenantCode rỗng")
    void utcid04_tenantcodeRong() {
        assertAuthError(AuthErrorCode.TENANT_CODE_REQUIRED, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode("").build()));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("UTCID05 [B] tenantCode chỉ có khoảng trắng")
    void utcid05_tenantcodeChiCoKhoangTrang() {
        assertAuthError(AuthErrorCode.TENANT_CODE_REQUIRED, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode("   ").build()));
        verifyNoInteractions(tenantRepository);
    }

    @Test
    @DisplayName("UTCID06 [B] Có khoảng trắng hai đầu → được cắt trước khi tra cứu")
    void utcid06_coKhoangTrangHaiDauDuocCatTruoc() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha")).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE)));
        TenantAuthResponse response = service.authenticateTenant(TenantAuthRequest.builder().tenantCode("  pk-alpha  ").build());
        assertEquals("pk-alpha", response.getTenantCode());
        assertEquals(LOGIN_URL, response.getLoginUrl());
        assertEquals(List.of(UserRole.TENANT_ADMIN_VALUE, UserRole.COUNTER_STAFF_VALUE), response.getAllowedRoles());
        verify(tenantRepository).findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha");
    }

    @Test
    @DisplayName("UTCID07 [N] Viết hoa → tra cứu không phân biệt hoa thường")
    void utcid07_vietHoaTraCuuKhongPhanBietHoa() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("PK-ALPHA")).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE)));
        TenantAuthResponse response = service.authenticateTenant(TenantAuthRequest.builder().tenantCode("PK-ALPHA").build());
        assertEquals("pk-alpha", response.getTenantCode());
        assertEquals(LOGIN_URL, response.getLoginUrl());
        assertEquals(List.of(UserRole.TENANT_ADMIN_VALUE, UserRole.COUNTER_STAFF_VALUE), response.getAllowedRoles());
        verify(tenantRepository).findByTenantCodeIgnoreCaseAndIsDeletedFalse("PK-ALPHA");
    }

    @Test
    @DisplayName("UTCID08 [N] Mã không tồn tại")
    void utcid08_maKhongTonTai() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("khong-ton-tai")).thenReturn(Optional.empty());
        assertAuthError(AuthErrorCode.TENANT_NOT_FOUND, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode("khong-ton-tai").build()));
    }

    @Test
    @DisplayName("UTCID09 [N] Cơ sở bị khóa (BLOCKED)")
    void utcid09_coSoBiKhoaBlocked() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha")).thenReturn(Optional.of(tenant(TenantStatus.BLOCKED)));
        assertAuthError(AuthErrorCode.TENANT_INACTIVE, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode("pk-alpha").build()));
    }

    @Test
    @DisplayName("UTCID10 [A] Cơ sở chờ duyệt (PENDING)")
    void utcid10_coSoChoDuyetPending() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha")).thenReturn(Optional.of(tenant(TenantStatus.PENDING)));
        assertAuthError(AuthErrorCode.TENANT_INACTIVE, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode("pk-alpha").build()));
    }

    @Test
    @DisplayName("UTCID11 [A] Cơ sở trạng thái DELETED")
    void utcid11_coSoTrangThaiDeleted() {
        when(tenantRepository.findByTenantCodeIgnoreCaseAndIsDeletedFalse("pk-alpha")).thenReturn(Optional.of(tenant(TenantStatus.DELETED)));
        assertAuthError(AuthErrorCode.TENANT_INACTIVE, () -> service.authenticateTenant(TenantAuthRequest.builder().tenantCode("pk-alpha").build()));
    }
}
