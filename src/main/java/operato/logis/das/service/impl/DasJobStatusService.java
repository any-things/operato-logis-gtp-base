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
	
	/**
	 * 스테이션 코드 값이 ALL인 값을 null로 변환하여 리턴
	 * 
	 * @param stationCd
	 * @return
	 */
	private String filterAllStation(String stationCd) {
		return ValueUtil.isEqualIgnoreCase(stationCd, LogisConstants.ALL_CAP_STRING) ? null : stationCd;
	}
	
	/**
	 * condition에 스테이션 코드 값이 있고 값이 ALL이면 condition에서 제거
	 *  
	 * @param condition
	 */
	private void filterAllStation(Map<String, Object> condition) {
		if(condition != null && condition.containsKey("stationCd")) {
			if(ValueUtil.isEqualIgnoreCase(LogisConstants.ALL_CAP_STRING, condition.get("stationCd").toString())) {
				condition.remove("stationCd");
			}
		}
	}
	
	@Override
	public List<JobInput> searchInputList(JobBatch batch, String equipCd, String stationCd, String selectedInputId) {
		// 태블릿의 현재 투입 정보 기준으로 2, 1 (next), 0 (current), -1 (previous) 정보를 표시
		Long domainId = batch.getDomainId();
		String sql = this.dasQueryStore.getDasFindStationWorkingInputSeq();
		
		// 해당 스테이션에 존재하는 피킹 중인 가장 작은 시퀀스를 조회
		stationCd = this.filterAllStation(stationCd);
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,stationCd,jobStatus", domainId, batch.getId(), stationCd, LogisConstants.JOB_STATUS_PICKING);
		Integer inputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
		
		// 없다면 해당 스테이션에 존재하는 투입 중인 가장 작은 시퀀스를 조회
		if(inputSeq == null || inputSeq < 0) {
			params.put("jobStatus", LogisConstants.JOB_STATUS_INPUT);
			inputSeq = this.queryManager.selectBySql(sql, params, Integer.class);
		}
		
		// 없다면 선택된 투입 ID를 중심으로 조회
		if((inputSeq == null || inputSeq < 0) && ValueUtil.isNotEmpty(selectedInputId)) {
			inputSeq = this.queryManager.selectBySql(sql, ValueUtil.newMap("domainId,jobInputId", domainId, selectedInputId), Integer.class);
		}
		
		// 그래도 없다면 해당 스테이션의 마지막 4개 투입 정보 조회
		if(inputSeq == null || inputSeq < 1) {
			params.put("lastFour", true);
		// 투입 순서로 작업을 위한 투입 리스트 조회
		} else {
			params.put("inputSeq", inputSeq);
		}
		
		sql = this.dasQueryStore.getDasWorkingJobInputListQuery();
		return this.queryManager.selectListBySql(sql, params, JobInput.class, 0, 0);
	}

	@Override
	public Page<JobInput> paginateInputList(JobBatch batch, String equipCd, String status, int page, int limit) {
		return this.paginateInputList(batch, equipCd, null, status, page, limit);
	}
	
	@Override
	public Page<JobInput> paginateInputList(JobBatch batch, String equipCd, String stationCd, String status, int page, int limit) {
		String sql = this.dasQueryStore.getDasBatchJobInputListQuery();
		status = ValueUtil.isEmpty(status) ? null : status;
		stationCd = this.filterAllStation(stationCd);
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,equipCd,stationCd,status", batch.getDomainId(), batch.getId(), equipCd, stationCd, status);
		return this.queryManager.selectPageBySql(sql.toString(), params, JobInput.class, page, limit);
	}

	@Override
	public List<JobInstance> searchInputJobList(JobBatch batch, JobInput input, String stationCd) {
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		stationCd = this.filterAllStation(stationCd);
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,inputSeq,stationCd", batch.getDomainId(), batch.getId(), input.getInputSeq(), stationCd);
		return this.queryManager.selectListBySql(sql, params, JobInstance.class, 0, 0);
	}

	@Override
	public List<JobInstance> searchInputJobList(JobBatch batch, Map<String, Object> condition) {
		this.filterAllStation(condition);
		this.addBatchConditions(batch, condition);
		return this.queryManager.selectList(JobInstance.class, condition);
	}

	@Override
	public List<JobInstance> searchPickingJobList(JobBatch batch, String stationCd, String classCd) {
		// 표시기 점등을 위해서 다른 테이블의 데이터도 필요해서 쿼리로 조회
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		stationCd = this.filterAllStation(stationCd);
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,classCd,statuses", batch.getDomainId(), batch.getId(), classCd, LogisConstants.JOB_STATUS_WIPC);
		return this.queryManager.selectListBySql(sql, params, JobInstance.class, 0, 0);
	}

	@Override
	public List<JobInstance> searchPickingJobList(JobBatch batch, Map<String, Object> condition) {
		// 표시기 점등을 위해서 다른 테이블의 데이터도 필요해서 쿼리로 조회
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		this.filterAllStation(condition);
		this.addBatchConditions(batch, condition);
		return this.queryManager.selectListBySql(sql, condition, JobInstance.class, 0, 0);
	}

	@Override
	public JobInstance findPickingJob(Long domainId, String jobInstanceId) {
		String sql = this.dasQueryStore.getSearchPickingJobListQuery();
		Map<String, Object> params = ValueUtil.newMap("domainId,jobInstanceId", domainId, jobInstanceId);
		List<JobInstance> jobList = this.queryManager.selectListBySql(sql, params, JobInstance.class, 1, 1);
		return ValueUtil.isEmpty(jobList) ? null : jobList.get(0);
	}

}
