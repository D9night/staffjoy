package xyz.staffjoy.company.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xyz.staffjoy.common.api.BaseResponse;
import xyz.staffjoy.common.api.ResultCode;
import xyz.staffjoy.common.auth.AuthConstant;
import xyz.staffjoy.common.auth.AuthContext;
import xyz.staffjoy.common.auth.Authorize;
import xyz.staffjoy.common.auth.PermissionDeniedException;
import xyz.staffjoy.common.error.ServiceException;
import xyz.staffjoy.company.dto.*;
import xyz.staffjoy.company.service.AdminService;
import xyz.staffjoy.company.service.PermissionService;

/**
 * 公司管理员Admin服务接口模型
 */
@RestController
@RequestMapping("/v1/company/admin")
@Validated
public class AdminController {

    @Autowired
    AdminService adminService;

    @Autowired
    PermissionService permissionService;


    //通过公司id获取管理员用户列表
    @GetMapping(path = "/list")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public ListAdminResponse listAdmins(@RequestParam String companyId) {
        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {
            permissionService.checkPermissionCompanyAdmin(companyId);
        }
        //通过公司id获取管理员用户列表
        AdminEntries adminEntries = adminService.listAdmins(companyId);
        return new ListAdminResponse(adminEntries);
    }

    //通过公司id和用户id获取管理员关系
    @GetMapping(path = "/get")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER,
            AuthConstant.AUTHORIZATION_WWW_SERVICE
    })
    public GenericDirectoryResponse getAdmin(@RequestParam String companyId, @RequestParam String userId) {
        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {
            permissionService.checkPermissionCompanyAdmin(companyId);
        }
        DirectoryEntryDto directoryEntryDto = adminService.getAdmin(companyId, userId);
        if (directoryEntryDto == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "admin relationship not found");
        }
        return new GenericDirectoryResponse(directoryEntryDto);
    }

    //创建用户和公司间的管理员关系
    @PostMapping(path = "/create")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER,
            AuthConstant.AUTHORIZATION_WWW_SERVICE
    })
    public GenericDirectoryResponse createAdmin(@RequestBody @Validated DirectoryEntryRequest request) {
        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {
            permissionService.checkPermissionCompanyAdmin(request.getCompanyId());
        }
        //创建用户和公司间的管理员关系
        DirectoryEntryDto directoryEntryDto = adminService.createAdmin(request.getCompanyId(), request.getUserId());
        return new GenericDirectoryResponse(directoryEntryDto);
    }

    @DeleteMapping(path = "/delete")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public BaseResponse deleteAdmin(@RequestBody @Validated DirectoryEntryRequest request) {
        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {
            permissionService.checkPermissionCompanyAdmin(request.getCompanyId());
        }
        adminService.deleteAdmin(request.getCompanyId(), request.getUserId());
        return BaseResponse.builder().build();
    }

    @GetMapping(path = "/admin_of")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_ACCOUNT_SERVICE,
            AuthConstant.AUTHORIZATION_WHOAMI_SERVICE,
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER,
            AuthConstant.AUTHORIZATION_WWW_SERVICE
    })
    public GetAdminOfResponse getAdminOf(@RequestParam String userId) {
        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {
            if (!userId.equals(AuthContext.getUserId())) {
                throw new PermissionDeniedException("You do not have access to this service");
            }
        }
        AdminOfList adminOfList = adminService.getAdminOf(userId);
        return new GetAdminOfResponse(adminOfList);
    }
}
