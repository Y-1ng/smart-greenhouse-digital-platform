package com.smartgreenhouse.api.modules.cooperation;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CooperationService {

    private final CooperationMapper mapper;

    public CooperationService(CooperationMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<VillageSummary> listVillages() {
        return mapper.findVillageShares().stream()
                .map(village -> new VillageSummary(
                        village.villageId(),
                        village.villageName(),
                        village.landArea(),
                        village.shareId(),
                        village.projectName(),
                        village.shareRatio(),
                        village.effectiveDate(),
                        village.dividendAmount(),
                        village.status()
                ))
                .toList();
    }

    @Transactional
    public CooperationShareView createShare(
            CooperationController.CreateShareRequest request
    ) {
        Long villageId = resolveVillageId(request);
        if (request.shareRatio() == null
                || request.shareRatio().compareTo(BigDecimal.ZERO) <= 0
                || request.shareRatio().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "股权比例必须在 0 到 1 之间"
            );
        }
        String status = normalizeStatus(request.status(), "ACTIVE");
        mapper.insertShare(new CooperationMapper.ShareInsert(
                villageId,
                request.projectName(),
                request.shareRatio(),
                request.effectiveDate(),
                status
        ));
        return toShareView(
                mapper.findShareByProjectName(request.projectName())
        );
    }

    private Long resolveVillageId(
            CooperationController.CreateShareRequest request
    ) {
        if (request.villageId() != null) {
            if (mapper.countVillage(request.villageId()) == 0) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "村庄不存在"
                );
            }
            return request.villageId();
        }

        String villageName = request.villageName() == null
                ? ""
                : request.villageName().trim();
        if (villageName.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "所属村庄不能为空"
            );
        }
        CooperationMapper.VillageIdRow existing =
                mapper.findVillageByName(villageName);
        if (existing != null) {
            return existing.villageId();
        }
        mapper.insertVillage(villageName);
        CooperationMapper.VillageIdRow created =
                mapper.findVillageByName(villageName);
        if (created == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "村庄创建失败"
            );
        }
        return created.villageId();
    }

    @Transactional
    public DividendView createDividend(
            CooperationController.CreateDividendRequest request
    ) {
        if (mapper.countShare(request.shareId()) == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "入股记录不存在"
            );
        }
        if (request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "分红金额必须大于零"
            );
        }
        String status = normalizeStatus(request.status(), "PENDING");
        LocalDateTime confirmedAt = "CONFIRMED".equals(status)
                ? LocalDateTime.now()
                : null;
        mapper.insertDividend(new CooperationMapper.DividendInsert(
                request.shareId(),
                request.periodName(),
                request.amount(),
                status,
                confirmedAt
        ));
        return toDividendView(mapper.findDividend(
                request.shareId(),
                request.periodName()
        ));
    }

    @Transactional
    public void deleteShare(Long shareId) {
        if (mapper.countShare(shareId) == 0) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "入股记录不存在"
            );
        }
        mapper.deleteDividendsByShareId(shareId);
        if (mapper.deleteShare(shareId) != 1) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "入股记录状态已变化"
            );
        }
    }

    private String normalizeStatus(String status, String defaultStatus) {
        return status == null || status.isBlank()
                ? defaultStatus
                : status.toUpperCase();
    }

    private CooperationShareView toShareView(
            CooperationMapper.ShareRow share
    ) {
        return new CooperationShareView(
                share.shareId(),
                share.villageId(),
                share.projectName(),
                share.shareRatio(),
                share.effectiveDate(),
                share.status()
        );
    }

    private DividendView toDividendView(
            CooperationMapper.DividendRow dividend
    ) {
        return new DividendView(
                dividend.dividendId(),
                dividend.shareId(),
                dividend.periodName(),
                dividend.amount(),
                dividend.status(),
                dividend.confirmedAt()
        );
    }
}
