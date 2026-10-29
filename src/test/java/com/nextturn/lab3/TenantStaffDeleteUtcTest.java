package com.nextturn.lab3;

import com.nextturn.dto.auth.CreateStaffRequest;
import com.nextturn.dto.auth.StaffResponse;
import com.nextturn.dto.auth.UpdateStaffRequest;
import com.nextturn.entity.CentralTenant;
import com.nextturn.entity.TenantStaff;
import com.nextturn.enums.StaffRole;
import com.nextturn.enums.StaffStatus;
import com.nextturn.exception.BaseErrorCode;
import com.nextturn.exception.BaseException;
import com.nextturn.exception.admin.AdminErrorCode;
import com.nextturn.exception.tenant.TenantErrorCode;
import com.nextturn.infrastructure.multitenancy.TenantContextHolder;
import com.nextturn.infrastructure.security.keycloak.KeycloakTokenService;
import com.nextturn.repository.auth.CentralTenantRepository;
import com.nextturn.repository.auth.TenantStaffRepository;
import com.nextturn.service.auth.TenantStaffService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * U18 – Xóa mềm nhân sự và xóa tài khoản Keycloak (REQ-STAFF-03).
 * Mã được kiểm thử: com.nextturn.service.auth.TenantStaffService#deleteStaff (repo NextTurn, branch auth-module, commit 6f9118b).
 * Mỗi phương thức ứng với một Unit Test Case trong "NextTurn - WhiteBoxTest.xlsx", sheet U18.
 */
@DisplayName("U18 - TenantStaffService.deleteStaff(staffId)")
class TenantStaffDeleteUtcTest {

    private static final String TENANT = "clinic-a";
    private static final String ORG = "org-001";

    private TenantStaffRepository staffRepository;
    private CentralTenantRepository tenantRepository;
    private KeycloakTokenService keycloak;
    private TenantStaffService service;

    @BeforeEach
    void setUp() {
        staffRepository = mock(TenantStaffRepository.class);
        tenantRepository = mock(CentralTenantRepository.class);
        keycloak = mock(KeycloakTokenService.class);
        service = new TenantStaffService(staffRepository, tenantRepository, keycloak);
        TenantContextHolder.setTenantCode(TENANT);
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.of(tenant(ORG)));
        when(staffRepository.save(any(TenantStaff.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static CentralTenant tenant(String orgId) {
        return CentralTenant.builder().id("tenant-001").tenantCode(TENANT).tenantName("Phong kham A").organisationId(orgId).build();
    }

    /** Nhân sự đang có trong DB (id staff-001). */
    private TenantStaff existing(StaffRole role, StaffStatus status, String keycloakUserId) {
        TenantStaff staff = TenantStaff.builder().id("staff-001").staffCode("STF-000001").keycloakUserId(keycloakUserId)
                .fullName("Nguyen Van An").email("an@clinic.vn").phoneNumber("+84901234567")
                .role(role).status(status).isDeleted(false).build();
        when(staffRepository.findByIdAndIsDeletedFalse("staff-001")).thenReturn(Optional.of(staff));
        return staff;
    }

    private void keycloakCreates(String keycloakUserId) {
        when(keycloak.createStaffUser(eq(TENANT), anyString(), anyString(), any(), any(StaffRole.class))).thenReturn(keycloakUserId);
    }

    private void dbSaveFails() {
        when(staffRepository.save(any(TenantStaff.class))).thenThrow(new RuntimeException("DB error"));
    }

    /** Có lời gọi KeycloakTokenService.method với đúng các tham số này hay không. */
    private boolean called(String method, Object... args) {
        return mockingDetails(keycloak).getInvocations().stream()
                .anyMatch(i -> i.getMethod().getName().equals(method) && Arrays.equals(i.getArguments(), args));
    }

    private static void assertError(BaseErrorCode expected, Executable call) {
        BaseException ex = assertThrows(BaseException.class, call);
        assertEquals(expected.code(), ex.getCode());
        assertEquals(expected.httpStatus(), ex.getHttpStatus());
    }

    @Test
    @DisplayName("UTCID01 [N] Xóa COUNTER: lưu xóa mềm rồi xóa tài khoản Keycloak")
    void utcid01_xoaCounterLuuXoaMemRoiXoaTai() {
        TenantStaff s = existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        service.deleteStaff("staff-001");
        assertTrue(s.getIsDeleted());
        assertEquals(StaffStatus.INACTIVE, s.getStatus());
        InOrder order = inOrder(staffRepository, keycloak);
        order.verify(staffRepository).save(s);
        order.verify(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
    }

    @Test
    @DisplayName("UTCID02 [N] Xóa ADMIN")
    void utcid02_xoaAdmin() {
        TenantStaff s = existing(StaffRole.ADMIN, StaffStatus.ACTIVE, "kc-001");
        service.deleteStaff("staff-001");
        assertTrue(s.getIsDeleted());
        verify(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
    }

    @Test
    @DisplayName("UTCID03 [N] Xóa SPECIALIST (không có tài khoản) → chỉ xóa mềm")
    void utcid03_xoaSpecialistKhongCoTaiKhoanChiXoa() {
        TenantStaff s = existing(StaffRole.SPECIALIST, StaffStatus.ACTIVE, null);
        service.deleteStaff("staff-001");
        assertTrue(s.getIsDeleted());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID04 [A] Thiếu tenant context")
    void utcid04_thieuTenantContext() {
        TenantContextHolder.clear();
        assertError(TenantErrorCode.TENANT_CONTEXT_MISSING, () -> service.deleteStaff("staff-001"));
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 [A] Không tìm thấy nhân sự")
    void utcid05_khongTimThayNhanSu() {
        assertError(AdminErrorCode.STAFF_USER_NOT_FOUND, () -> service.deleteStaff("staff-404"));
        verify(staffRepository, never()).save(any());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID06 [A] Không tìm thấy cơ sở → không xóa gì")
    void utcid06_khongTimThayCoSoKhongXoaGi() {
        TenantStaff s = existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        when(tenantRepository.findByTenantCodeAndIsDeletedFalse(TENANT)).thenReturn(Optional.empty());
        assertError(TenantErrorCode.TENANT_NOT_FOUND, () -> service.deleteStaff("staff-001"));
        assertFalse(s.getIsDeleted());
        verify(staffRepository, never()).save(any());
        verifyNoInteractions(keycloak);
    }

    @Test
    @DisplayName("UTCID07 [A] Xóa Keycloak lỗi → phải báo lỗi hoặc khóa tài khoản")
    void utcid07_xoaKeycloakLoiPhaiBaoLoiHoacKhoa() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        doThrow(new RuntimeException("Keycloak error")).when(keycloak).deleteStaffUserInKeycloak(ORG, "kc-001");
        boolean failed = false;
        try {
            service.deleteStaff("staff-001");
        } catch (RuntimeException ex) {
            failed = true;
        }
        assertTrue(failed || called("updateUserStatusInKeycloak", "kc-001", false), "Xóa nhân sự báo thành công nhưng tài khoản Keycloak kc-001 vẫn hoạt động");
    }

    @Test
    @DisplayName("UTCID08 [A] Lưu DB lỗi → ném lỗi, chưa xóa tài khoản Keycloak")
    void utcid08_luuDbLoiNemLoiChuaXoaTai() {
        existing(StaffRole.COUNTER, StaffStatus.ACTIVE, "kc-001");
        dbSaveFails();
        assertThrows(RuntimeException.class, () -> service.deleteStaff("staff-001"));
        verify(keycloak, never()).deleteStaffUserInKeycloak(any(), any());
    }
}
