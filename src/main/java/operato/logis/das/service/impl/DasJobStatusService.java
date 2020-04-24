package operato.logis.das.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import operato.logis.das.query.store.DasQueryStore;
import xyz.anythings.base.LogisConstants;
import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInput;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.service.impl.AbstractJobStatusService;
import xyz.elidom.dbist.dml.Page;
import xyz.elidom.sys.util.ValueUtil;

/**
 * 출고 작업 상태 서비스
 * 
 * @author shortstop
 */
@Component("dasJobStatusService")
public class DasJobStatusService extends AbstractJobStatusService {

	/**
	 * 출고 배치 관련 쿼리 스토어 
	 */
	@Autowired
	protected DasQueryStore dasQueryStore;
	
	@Override
	public List<JobInput> searchInputList(JobBatch batch, String equipCd, String stationCd, String selectedInputId) {
		// 태블릿의 현재 투입 정보 기준으로 2, 1 (next), 0 (current), -1 (previous) 정보를 표시
		Long domainId = batch.getDomainId();
		String sql = this.dasQueryStore.getDasFindStationWorkingInputSeq();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,stationCd,status", domainId, batch.getId(), stationCd, JobInput.INPUT_STATUS_RUNNING);
		Integer inputSeq = null;
		
		if(ValueUtil.isEmpty(selectedInputId)) {
			// 해당 스테이션에 존재하는 진행 중인 투입 시퀀스를 중심으로 조회
			inputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
			
			if(inputSeq == null) {
				params.remove("status");
				inputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
			}
		} else {
			// selectedInputId로 투입 시퀀스 조회
			inputSeq = this.queryManager.selectBySql(sql, ValueUtil.newMap("domainId,id", domainId, selectedInputId), Integer.class);
		}
		
		if(inputSeq == null || inputSeq < 1) {
			return null;
		}
		
		// inputSeq로 작업을 위한 투입 리스트 조회
		params.remove("status");
		params.put("inputSeq", inputSeq);
		sql = this.dasQueryStore.getDasWorkingJobInputListQuery();
		return this.queryManager.selectListBySql(sql, params, JobInput.class, 0, 0);
	}

	@Override
	public Page<JobInput> paginateInputList(JobBatch batch, String equipCd, String status, int page, int limit) {
		String sql = this.dasQueryStore.getDasBatchJobInputListQuery();
		List<String> statuses = (ValueUtil.isNotEmpty(status) && ValueUtil.isEqualIgnoreCase(status, "U")) ? LogisConstants.JOB_STATUS_WIPC : null;
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,equipCd,statuses", batch.getDomainId(), batch.getId(), equipCd, statuses);
		return this.queryManager.selectPageBySql(sql.toString(), params, JobInput.class, page, limit);
	}

	@Override
	public List<JobInstance> searchInputJobList(JobBatch batch, JobInput input, String stationCd) {
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,inputSeq,stationCd", batch.getDomainId(), batch.getId(), input.getInputSeq(), stationCd);
		return this.queryManager.selectListBySql(sql, params, JobInstance.class, 0, 0);
	}

	@Override
	public List<JobInstance> searchInputJobList(JobBatch batch, Map<String, Object> condition) {
		this.addBatchConditions(batch, condition);
		return this.queryManager.selectList(JobInstance.class, condition);
	}

	@Override
	public List<JobInstance> searchPickingJobList(JobBatch batch, String stationCd, String classCd) {
		// 표시기 점등을 위해서 다른 테이블의 데이터도 필요해서 쿼리로 조회
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,classCd,statuses", batch.getDomainId(), batch.getId(), classCd, LogisConstants.JOB_STATUS_WIPC);
		return this.queryManager.selectListBySql(sql, params, JobInstance.class, 0, 0);
	}

	@Override
	public List<JobInstance> searchPickingJobList(JobBatch batch, Map<String, Object> condition) {
		// 표시기 점등을 위해서 다른 테이블의 데이터도 필요해서 쿼리로 조회
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		this.addBatchConditions(batch, condition);
		return this.queryManager.selectListBySql(sql, condition, JobInstance.class, 0, 0);
	}

	@Override
	public JobInstance findPickingJob(Long domainId, String jobInstanceId) {
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,id", domainId, jobInstanceId);
		List<JobInstance> jobList = this.queryManager.selectListBySql(sql, params, JobInstance.class, 1, 1);
		return ValueUtil.isEmpty(jobList) ? null : jobList.get(0);
	}

}
