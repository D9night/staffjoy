package xyz.staffjoy.company.service;

import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import xyz.staffjoy.common.api.ResultCode;
import xyz.staffjoy.common.auditlog.LogEntry;
import xyz.staffjoy.common.auth.AuthContext;
import xyz.staffjoy.common.error.ServiceException;
import xyz.staffjoy.company.dto.CompanyDto;
import xyz.staffjoy.company.dto.CompanyList;
import xyz.staffjoy.company.model.Company;
import xyz.staffjoy.company.repo.CompanyRepo;
import xyz.staffjoy.company.service.helper.ServiceHelper;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
public class CompanyService {

    static final ILogger logger = SLoggerFactory.getLogger(CompanyService.class);

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private ServiceHelper serviceHelper;

    @Autowired
    private ModelMapper modelMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 创建公司
     * @param companyDto
     * @return
     */
    public CompanyDto createCompany(CompanyDto companyDto) {
        //DTO转化到DMO
        Company company = this.convertToModel(companyDto);

        Company savedCompany = null;
        try {
            savedCompany = companyRepo.save(company);
        } catch (Exception ex) {
            String errMsg = "could not create company";
            //将异常和错误日志发送到在线sentry云服务上
            serviceHelper.handleErrorAndThrowException(logger, ex, errMsg);
        }

        //审计日志
        LogEntry auditLog = LogEntry.builder()
                .currentUserId(AuthContext.getUserId())
                .authorization(AuthContext.getAuthz())
                .targetType("company")
                .targetId(company.getId())
                .companyId(company.getId())
                .teamId("")
                .updatedContents(company.toString())
                .build();

        logger.info("created company", auditLog);

        //同步用户事件到Intercom客服系统
        serviceHelper.trackEventAsync("company_created");

        //DMO转化到DTO
        return this.convertToDto(savedCompany);
    }

    /**
     * 获取现有公司列表 内部使用
     * @param offset
     * @param limit
     * @return
     */
    public CompanyList listCompanies(int offset, int limit) {

        if (limit <= 0) {
            limit = 20;
        }

        Pageable pageRequest = PageRequest.of(offset, limit);
        Page<Company> companyPage = null;
        try {
            //分页查询
            companyPage = companyRepo.findAll(pageRequest);
        } catch (Exception ex) {
            String errMsg = "fail to query database for company list";
            //将异常和错误日志发送到在线sentry云服务上
            serviceHelper.handleErrorAndThrowException(logger, ex, errMsg);
        }
        List<CompanyDto> companyDtoList = companyPage.getContent().stream().map(company -> convertToDto(company)).collect(toList());

        return CompanyList.builder()
                .limit(limit)
                .offset(offset)
                .companies(companyDtoList)
                .build();
}

    /**
     * 通过id获取公司
     * @param companyId
     * @return
     */
    public CompanyDto getCompany(String companyId) {

        Company company = companyRepo.findCompanyById(companyId);
        if (company == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "Company not found");
        }

        //DMO转化到DTO
        return this.convertToDto(company);

    }

    /**
     * 更新公司信息
     * @param companyDto
     * @return
     */
    public CompanyDto updateCompany(CompanyDto companyDto) {
        Company existingCompany = companyRepo.findCompanyById(companyDto.getId());
        if (existingCompany == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "Company not found");
        }
        entityManager.detach(existingCompany);

        Company companyToUpdate = this.convertToModel(companyDto);
        Company updatedCompany = null;
        try {
            updatedCompany = companyRepo.save(companyToUpdate);
        } catch (Exception ex) {
            String errMsg = "could not update the companyDto";
            //将异常和错误日志发送到在线sentry云服务上
            serviceHelper.handleErrorAndThrowException(logger, ex, errMsg);
        }

        //审计日志
        LogEntry auditLog = LogEntry.builder()
                .currentUserId(AuthContext.getUserId())
                .authorization(AuthContext.getAuthz())
                .targetType("company")
                .targetId(companyToUpdate.getId())
                .companyId(companyToUpdate.getId())
                .teamId("")
                .originalContents(existingCompany.toString())
                .updatedContents(updatedCompany.toString())
                .build();

        //结构化日志
        logger.info("updated company", auditLog);

        //同步用户事件到Intercom客服系统
        serviceHelper.trackEventAsync("company_updated");

        //DMO转化到DTO
        return this.convertToDto(updatedCompany);
    }

    /**
     * DMO转化到DTO
     * @param company
     * @return
     */
    private CompanyDto convertToDto(Company company) {
        return modelMapper.map(company, CompanyDto.class);
    }

    /**
     * DTO转化到DMO
     * @param companyDto
     * @return
     */
    private Company convertToModel(CompanyDto companyDto) {
        return modelMapper.map(companyDto, Company.class);
    }
}
