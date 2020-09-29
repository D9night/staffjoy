package xyz.staffjoy.company.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xyz.staffjoy.common.api.BaseResponse;
import xyz.staffjoy.common.auth.AuthConstant;
import xyz.staffjoy.common.validation.Group1;
import xyz.staffjoy.common.validation.Group2;
import xyz.staffjoy.company.CompanyConstant;
import xyz.staffjoy.company.dto.*;

@FeignClient(name = CompanyConstant.SERVICE_NAME, path = "/v1/company", url = "${staffjoy.company-service-endpoint}")
public interface CompanyClient {
    // Company Apis 公司Company服务接口模型
    //创建公司  需要授权header
    @PostMapping(path = "/create")
    GenericCompanyResponse createCompany(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                         @RequestBody @Validated({Group2.class}) CompanyDto companyDto);

    //获取现有公司列表 内部使用
    @GetMapping(path = "/list")
    ListCompanyResponse listCompanies(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                      @RequestParam int offset, @RequestParam int limit);

    //通过id获取公司
    @GetMapping(path= "/get")
    GenericCompanyResponse getCompany(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                      @RequestParam("company_id") String companyId);

    //更新公司信息
    @PutMapping(path= "/update")
    GenericCompanyResponse updateCompany(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                         @RequestBody @Validated({Group1.class}) CompanyDto companyDto);

    // Admin Apis 公司管理员Admin服务接口模型
    //通过公司id获取管理员用户列表
    @GetMapping(path = "/admin/list")
    ListAdminResponse listAdmins(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                 @RequestParam String companyId);

    //通过公司id和用户id获取管理员关系
    @GetMapping(path = "/admin/get")
    GenericDirectoryResponse getAdmin(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                      @RequestParam String companyId, @RequestParam String userId);

    //创建用户和公司间的管理员关系
    @PostMapping(path = "/admin/create")
    GenericDirectoryResponse createAdmin(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                         @RequestBody @Validated DirectoryEntryRequest request);

    //删除用户和公司间的管理员关系
    @DeleteMapping(path = "/admin/delete")
    BaseResponse deleteAdmin(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                             @RequestBody @Validated DirectoryEntryRequest request);

    //通过用户id获取其管理的公司列表
    @GetMapping(path = "/admin/admin_of")
    GetAdminOfResponse getAdminOf(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String userId);

    // Directory Apis 员工目录Directory服务接口模型

    //将某用户添加到公司员工目录中
    @PostMapping(path = "/directory/create")
    GenericDirectoryResponse createDirectory(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                             @RequestBody @Validated NewDirectoryEntry request);

    //列出某公司id下的所有员工目录项
    @GetMapping(path = "/directory/list")
    ListDirectoryResponse listDirectories(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                          @RequestParam String companyId, @RequestParam int offset, @RequestParam int limit);

    //通过公司id和用户id查询某员工目录项
    @GetMapping(path = "/directory/get")
    GenericDirectoryResponse getDirectoryEntry(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                               @RequestParam String companyId, @RequestParam String userId);

    //更新员工目录项
    @PutMapping(path = "/directory/update")
    GenericDirectoryResponse updateDirectoryEntry(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                                  @RequestBody @Validated DirectoryEntryDto request);

    //获取某公司id下的所有员工目录项，包括是否管理员，对应团队信息
    @GetMapping(path = "/directory/get_associations")
    GetAssociationResponse getAssociations(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                           @RequestParam String companyId, @RequestParam int offset, @RequestParam int limit);

    // WorkerDto Apis 雇员Worker服务接口模型

    //列出某公司id和团队id下的所有雇员目录项
    @GetMapping(path = "/worker/list")
    ListWorkerResponse listWorkers(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                   @RequestParam String companyId, @RequestParam String teamId);

    //获取某公司id、团队id和用户id的雇员目录项
    @GetMapping(path = "/worker/get")
    GenericDirectoryResponse getWorker(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                       @RequestParam  String companyId, @RequestParam String teamId, @RequestParam String userId);

    //删除某用户和某公司/团队间的雇员关系
    @DeleteMapping(path = "/worker/delete")
    BaseResponse deleteWorker(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                              @RequestBody @Validated WorkerDto workerDto);

    //获取某用户id所隶属的团队
    @GetMapping(path = "/worker/get_worker_of")
    GetWorkerOfResponse getWorkerOf(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String userId);

    //建立某用户和某公司/团队间的雇员关系
    @PostMapping(path = "/worker/create")
    GenericDirectoryResponse createWorker(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                          @RequestBody @Validated WorkerDto workerDto);

    // Team Apis 团队Team服务接口模型
    //创建团队
    @PostMapping(path = "/team/create")
    GenericTeamResponse createTeam(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                   @RequestBody @Validated CreateTeamRequest request);

    //列出某公司id下的所有团队
    @GetMapping(path = "/team/list")
    ListTeamResponse listTeams(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String companyId);

    //通过公司id和团队id获取团队信息
    @GetMapping(path = "/team/get")
    GenericTeamResponse getTeam(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                @RequestParam String companyId, @RequestParam String teamId);

    //更新团队信息
    @PutMapping(path = "/team/update")
    GenericTeamResponse updateTeam(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated TeamDto teamDto);

    //通过公司id和用户id查询该员工隶属团队
    @GetMapping(path = "/team/get_worker_team_info")
    GenericWorkerResponse getWorkerTeamInfo(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz,
                                            @RequestParam(required = false) String companyId, @RequestParam String userId);

    // Job Apis 任务Job服务接口模型

    //为某公司/团队新建任务
    @PostMapping(path = "/job/create")
    GenericJobResponse createJob(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated CreateJobRequest request);

    //列出某公司/团队下的所有任务
    @GetMapping(path = "/job/list")
    ListJobResponse listJobs(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String companyId, @RequestParam String teamId);

    //获取某公司id，团队id和任务id所对应的任务
    @GetMapping(path = "/job/get")
    GenericJobResponse getJob(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String jobId, @RequestParam String companyId, @RequestParam String teamId);

    //更新任务信息
    @PutMapping(path = "/job/update")
    GenericJobResponse updateJob(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated JobDto jobDto);

    // Shift Apis 班次Shift服务接口模型

    //在某公司/团队下创建新班次
    @PostMapping(path = "/shift/create")
    GenericShiftResponse createShift(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated CreateShiftRequest request);

    //通过公司/团队/雇员id和时间范围等信息查询对应班次
    @PostMapping(path = "/shift/list_worker_shifts")
    GenericShiftListResponse listWorkerShifts(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated WorkerShiftListRequest request);

    //通过公司/团队/用户/任务id，时间范围等信息查询对应班次
    @PostMapping(path = "/shift/list_shifts")
    GenericShiftListResponse listShifts(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated ShiftListRequest request);

    //批量发布班次
    @PostMapping(path = "/shift/bulk_publish")
    GenericShiftListResponse bulkPublishShifts(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated BulkPublishShiftsRequest request);

    //通过班次id获取班次信息
    @GetMapping(path = "/shift/get")
    GenericShiftResponse getShift(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String shiftId, @RequestParam String teamId, @RequestParam  String companyId);

    //更新某班次信息
    @PutMapping(path = "/shift/update")
    GenericShiftResponse updateShift(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestBody @Validated ShiftDto shiftDto);

    //删除某个班次
    @DeleteMapping(path = "/shift/delete")
    BaseResponse deleteShift(@RequestHeader(AuthConstant.AUTHORIZATION_HEADER) String authz, @RequestParam String shiftId, @RequestParam String teamId, @RequestParam String companyId);
}
